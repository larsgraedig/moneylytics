package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.adapter.output.persistence.TransactionImportPreviewSessionPersistenceAdapter
import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.FindIgnoredFingerprintsUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ImportFileSpec
import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.UpdateIgnoredTransactionsUseCase
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.domain.AccountBalance
import com.moneylytics.api.domain.CategoryClassifierFeatures
import com.moneylytics.api.domain.ImportFileType
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
import java.time.LocalDateTime
import java.util.UUID

private const val CAMT_PREVIEW_SESSION_TTL_HOURS = 24L

@RestController
@RequestMapping("/transactions")
class CamtImportController(
    private val camtParser: CamtParser,
    private val checkDuplicatesUseCase: CheckDuplicatesUseCase,
    private val findIgnoredFingerprintsUseCase: FindIgnoredFingerprintsUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val updateIgnoredTransactionsUseCase: UpdateIgnoredTransactionsUseCase,
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val enrichTransactionUseCase: EnrichTransactionUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
    private val importPreviewSessionAdapter: TransactionImportPreviewSessionPersistenceAdapter,
    private val categoryClassifier: CategoryClassifier,
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
        val rowSourceFilename = mutableMapOf<Int, String>()
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
                    renumbered.forEach { rowSourceFilename[it.rowNumber] = filePart.filename() }
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

        val previewRows =
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
                    sourceFilename = rowSourceFilename[row.rowNumber],
                )
            }

        val rowsWithSuggestions = attachCategorySuggestions(previewRows, organizationId)
        val previewToken = UUID.randomUUID()
        withContext(Dispatchers.IO) {
            importPreviewSessionAdapter.save(
                previewToken,
                rowsWithSuggestions,
                LocalDateTime.now().plusHours(CAMT_PREVIEW_SESSION_TTL_HOURS),
            )
        }

        val response =
            CamtPreviewResponse(
                rows = rowsWithSuggestions,
                accounts = accounts,
                accountBalances = mergedBalances,
                previewToken = previewToken,
            )

        return ResponseEntity.ok(response)
    }

    private suspend fun attachCategorySuggestions(
        previewRows: List<RawPreviewRow>,
        organizationId: Long,
    ): List<RawPreviewRow> {
        val features =
            previewRows.map { row ->
                CategoryClassifierFeatures(
                    purpose = row.purpose.ifBlank { null },
                    counterpartyName = row.counterparty.ifBlank { null },
                    counterpartyIban = row.counterpartyIban,
                    amount = row.amount,
                )
            }
        val suggestions =
            withContext(Dispatchers.IO) {
                categoryClassifier.suggestAll(organizationId, features)
            }
        return previewRows.zip(suggestions).map { (row, catId) ->
            if (row.status == RowStatus.NEW && !row.unknownAccount) row.copy(suggestedCategoryId = catId) else row
        }
    }

    @PostMapping("/camt/import")
    suspend fun importCamt(
        @RequestBody request: CamtImportByTokenRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<out Any> {
        val sessionRows =
            withContext(Dispatchers.IO) { importPreviewSessionAdapter.load(request.previewToken, RawPreviewRow::class.java) }
                ?: return ResponseEntity.notFound().build()

        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val knownIbans =
            withContext(Dispatchers.IO) { getAccountsUseCase.getAccounts(organizationId) }
                .map { it.iban }
                .toSet()

        val excludedSet = request.excludedRowIndices.toSet()
        val selectedRows = sessionRows.filter { it.rowNumber !in excludedSet }
        val safeRows =
            if (knownIbans.isEmpty()) selectedRows else selectedRows.filter { it.accountIban in knownIbans }

        withContext(Dispatchers.IO) {
            updateIgnoredTransactionsUseCase.update(
                toIgnore = request.toIgnore,
                toUnignore = safeRows.mapNotNull { it.fingerprint },
                organizationId = organizationId,
            )
        }

        val transactions =
            safeRows.map { row ->
                Transaction(
                    category = "",
                    subcategory = null,
                    group = "",
                    bookingDate = LocalDate.parse(row.bookingDate),
                    valueDate = LocalDate.parse(row.valueDate),
                    accountingDate = LocalDate.parse(row.bookingDate),
                    amount = requireNotNull(row.amount),
                    currency = row.currency,
                    accountIban = row.accountIban,
                    purpose = row.purpose.ifBlank { null },
                    counterpartyName = row.counterparty.ifBlank { null },
                    counterpartyIban = row.counterpartyIban,
                )
            }

        val accountBalances =
            request.accountBalances.mapValues { (_, b) ->
                AccountBalance(amount = b.amount, date = LocalDate.parse(b.date))
            }

        val fileSpecs =
            safeRows
                .groupBy { it.sourceFilename ?: "camt-import" }
                .map { (filename, rows) ->
                    val fingerprints = rows.mapNotNull { it.fingerprint }
                    val checksum =
                        sha256(
                            fingerprints.sorted().joinToString(",").toByteArray(Charsets.UTF_8),
                        )
                    ImportFileSpec(
                        filename = filename,
                        checksum = checksum,
                        fileType = ImportFileType.CAMT,
                        fingerprints = fingerprints,
                    )
                }

        val importResult =
            withContext(Dispatchers.IO) {
                importTransactionsUseCase.importTransactions(
                    ImportTransactionsCommand(
                        transactions = transactions,
                        accountNames = request.accountNames,
                        accountBalances = accountBalances,
                        organizationId = organizationId,
                        files = fileSpecs,
                    ),
                )
            }

        request.toEnrich.forEach { e ->
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

        withContext(Dispatchers.IO) { importPreviewSessionAdapter.delete(request.previewToken) }

        return ResponseEntity.ok(ImportSuccessResponse(importedCount = importResult.importedCount, importId = importResult.importId))
    }

    private fun ParsedRawRow.toPreviewRow(
        status: RowStatus,
        fingerprint: String?,
        unknownAccount: Boolean = false,
        sourceFilename: String? = null,
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
        sourceFilename = sourceFilename,
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
