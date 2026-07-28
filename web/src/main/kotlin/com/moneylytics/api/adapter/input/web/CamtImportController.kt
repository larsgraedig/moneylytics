package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.FindIgnoredFingerprintsUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.SaveCategoriesUseCase
import com.moneylytics.api.application.port.input.UpdateIgnoredTransactionsUseCase
import com.moneylytics.api.domain.AccountBalance
import com.moneylytics.api.domain.Category
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.time.LocalDate

@RestController
@RequestMapping("/transactions")
class CamtImportController(
    private val camtParser: CamtParser,
    private val checkDuplicatesUseCase: CheckDuplicatesUseCase,
    private val findIgnoredFingerprintsUseCase: FindIgnoredFingerprintsUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val updateIgnoredTransactionsUseCase: UpdateIgnoredTransactionsUseCase,
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val saveCategoriesUseCase: SaveCategoriesUseCase,
    private val enrichTransactionUseCase: EnrichTransactionUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    companion object {
        private const val HTTP_UNPROCESSABLE_ENTITY = 422
    }

    @PostMapping("/camt/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun preview(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<out Any> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)

        val parts = exchange.multipartData.awaitSingle()
        val fileParts = parts["files"]?.filterIsInstance<FilePart>() ?: emptyList()

        if (fileParts.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "No files provided"))
        }

        val allRows = mutableListOf<ParsedRawRow>()
        val mergedBalances = mutableMapOf<String, CamtAccountBalance>()
        var nextRowNumber = 1

        for (filePart in fileParts) {
            val bytes = filePart.readBytes()
            when (val result = camtParser.parse(bytes)) {
                is CamtParseResult.FormatError ->
                    return ResponseEntity
                        .status(HTTP_UNPROCESSABLE_ENTITY)
                        .body(mapOf("error" to "${filePart.filename()}: ${result.message}"))

                is CamtParseResult.Success -> {
                    val renumbered = result.rows.mapIndexed { idx, row -> row.copy(rowNumber = nextRowNumber + idx) }
                    allRows += renumbered
                    nextRowNumber += result.rows.size
                    result.accountBalances.forEach { (iban, balance) ->
                        mergedBalances[iban] = CamtAccountBalance(amount = balance.amount, date = balance.date.toString())
                    }
                }
            }
        }

        val validRows = allRows.filter { it.isValid }
        val rowFingerprints = assignFingerprints(validRows)
        val allFingerprints = rowFingerprints.values.toSet()

        val existingFingerprints =
            if (allFingerprints.isEmpty()) emptySet() else checkDuplicatesUseCase.findExistingFingerprints(allFingerprints, organizationId)
        val ignoredFingerprints =
            if (allFingerprints.isEmpty()) {
                emptySet()
            } else {
                findIgnoredFingerprintsUseCase.findIgnoredFingerprints(
                    allFingerprints,
                    organizationId,
                )
            }
        val knownIbans =
            withContext(Dispatchers.IO) { getAccountsUseCase.getAccounts(organizationId) }.map { it.iban }.toSet()

        val accounts =
            allRows
                .filter { it.accountIban.isNotBlank() }
                .distinctBy { it.accountIban }
                .map { CamtAccountInfo(iban = it.accountIban, suggestedName = it.accountName) }

        val response =
            CamtPreviewResponse(
                rows =
                    allRows.map { row ->
                        val fp = rowFingerprints[row.rowNumber]
                        val status =
                            when {
                                !row.isValid -> RowStatus.INVALID
                                fp in existingFingerprints -> RowStatus.DUPLICATE
                                fp in ignoredFingerprints -> RowStatus.PREVIOUSLY_IGNORED
                                else -> RowStatus.NEW
                            }
                        row.toPreviewRow(
                            status = status,
                            fingerprint = fp,
                            unknownAccount = knownIbans.isNotEmpty() && row.accountIban !in knownIbans,
                        )
                    },
                accounts = accounts,
                accountBalances = mergedBalances,
            )

        return ResponseEntity.ok(response)
    }

    @PostMapping("/camt/import")
    suspend fun importCamt(
        @RequestBody request: CamtImportRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<out Any> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val knownIbans =
            withContext(Dispatchers.IO) { getAccountsUseCase.getAccounts(organizationId) }
                .map { it.iban }
                .toSet()

        val safeToImport =
            if (knownIbans.isEmpty()) {
                request.toImport
            } else {
                request.toImport.filter { it.accountIban in knownIbans }
            }
        val safeRequest = request.copy(toImport = safeToImport)

        withContext(Dispatchers.IO) {
            updateIgnoredTransactionsUseCase.update(
                toIgnore = safeRequest.toIgnore,
                toUnignore = safeRequest.toImport.map { it.fingerprint },
                organizationId = organizationId,
            )
        }

        val categories =
            safeRequest.toImport
                .map { Category(name = it.category, subcategory = it.subcategory, group = it.categoryGroup) }
                .distinct()
        withContext(Dispatchers.IO) { saveCategoriesUseCase.saveCategories(categories, organizationId) }

        val transactions =
            safeRequest.toImport.map { row ->
                Transaction(
                    category = row.category,
                    subcategory = row.subcategory,
                    categoryGroup = row.categoryGroup,
                    bookingDate = LocalDate.parse(row.bookingDate),
                    valueDate = LocalDate.parse(row.valueDate),
                    accountingDate = LocalDate.parse(row.bookingDate),
                    amount = row.amount,
                    currency = row.currency,
                    accountIban = row.accountIban,
                    purpose = row.purpose,
                    counterpartyName = row.counterpartyName,
                    counterpartyIban = row.counterpartyIban,
                )
            }

        val accountBalances =
            safeRequest.accountBalances.orEmpty().mapValues { (_, b) ->
                AccountBalance(amount = b.amount, date = LocalDate.parse(b.date))
            }

        val importedCount =
            withContext(Dispatchers.IO) {
                importTransactionsUseCase.importTransactions(
                    ImportTransactionsCommand(
                        transactions = transactions,
                        accountNames = safeRequest.accountNames,
                        accountBalances = accountBalances,
                        organizationId = organizationId,
                    ),
                )
            }

        safeRequest.toEnrich.orEmpty().forEach { e ->
            withContext(Dispatchers.IO) {
                enrichTransactionUseCase.enrichByFingerprint(
                    e.fingerprint,
                    organizationId,
                    e.purpose,
                    e.counterpartyName,
                    e.counterpartyIban,
                )
            }
        }

        return ResponseEntity.ok(ImportSuccessResponse(importedCount = importedCount))
    }

    private fun ParsedRawRow.toPreviewRow(
        status: RowStatus,
        fingerprint: String?,
        unknownAccount: Boolean = false,
    ) = RawPreviewRow(
        rowNumber = rowNumber,
        status = status,
        bookingDate = bookingDate?.toString(),
        valueDate = valueDate?.toString(),
        counterparty = counterparty,
        purpose = purpose,
        amount = amount,
        amountRaw = amountRaw,
        currency = currency,
        accountIban = accountIban,
        accountName = accountName,
        fingerprint = fingerprint,
        errors = errors.map { RawPreviewError(column = it.column, value = it.value, message = it.message) },
        unknownAccount = unknownAccount,
        counterpartyIban = counterpartyIban,
    )

    private suspend fun FilePart.readBytes(): ByteArray =
        DataBufferUtils
            .join(content())
            .map { buffer ->
                val bytes = ByteArray(buffer.readableByteCount())
                buffer.read(bytes)
                DataBufferUtils.release(buffer)
                bytes
            }.awaitSingle()
}
