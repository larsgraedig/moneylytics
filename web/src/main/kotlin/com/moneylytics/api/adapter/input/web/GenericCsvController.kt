package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.adapter.output.persistence.CsvProfilePersistenceAdapter
import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
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
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.time.LocalDate

data class GenericCsvImportRequest(
    val toImport: List<GenericRowToImport>,
    val toEnrich: List<TransactionEnrichRequest> = emptyList(),
)

@RestController
@RequestMapping("/transactions/csv")
class GenericCsvController(
    private val detector: GenericCsvDetector,
    private val parser: GenericCsvParser,
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val checkDuplicatesUseCase: CheckDuplicatesUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val enrichTransactionUseCase: EnrichTransactionUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
    private val csvProfileAdapter: CsvProfilePersistenceAdapter,
) {
    @PostMapping("/detect", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun detect(
        @RequestPart("file") filePart: FilePart,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): CsvDetectionResult {
        val content = filePart.readUtf8()
        val detection = withContext(Dispatchers.Default) { detector.detect(content) }
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val savedMapping =
            withContext(Dispatchers.IO) {
                csvProfileAdapter.findMapping(organizationId, detection.fingerprint)
            }
        return detection.result.copy(savedMapping = savedMapping)
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun importGeneric(
        @RequestPart("file") filePart: FilePart,
        @RequestPart("mapping") mapping: GenericCsvMapping,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<ImportSuccessResponse> {
        val content = filePart.readUtf8()
        val (transactions, accountNames) = withContext(Dispatchers.Default) { parser.parse(content, mapping) }
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val count =
            withContext(Dispatchers.IO) {
                importTransactionsUseCase.importTransactions(
                    ImportTransactionsCommand(
                        transactions = transactions,
                        accountNames = accountNames,
                        organizationId = organizationId,
                    ),
                )
            }
        val fingerprint =
            withContext(Dispatchers.Default) {
                detector.computeFingerprint(
                    headers =
                        mapping.delimiter.firstOrNull()?.let { delim ->
                            content
                                .lines()
                                .firstOrNull { it.isNotBlank() }
                                ?.split(delim)
                                ?.map { it.trim() }
                        } ?: emptyList(),
                    delimiter = mapping.delimiter.firstOrNull() ?: ',',
                )
            }
        withContext(Dispatchers.IO) { csvProfileAdapter.saveMapping(organizationId, fingerprint, mapping) }
        return ResponseEntity.ok(ImportSuccessResponse(importedCount = count))
    }

    @PostMapping("/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun preview(
        @RequestPart("file") filePart: FilePart,
        @RequestPart("mapping") mapping: GenericCsvMapping,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): List<GenericCsvPreviewRow> {
        val content = filePart.readUtf8()
        val rows = withContext(Dispatchers.Default) { parser.preview(content, mapping) }
        if (rows.isEmpty()) return rows

        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val allFingerprints = rows.map { it.fingerprint }.toSet()
        val (existingFingerprints, knownIbans) =
            withContext(Dispatchers.IO) {
                checkDuplicatesUseCase.findExistingFingerprints(allFingerprints, organizationId) to
                    getAccountsUseCase.getAccounts(organizationId).map { it.iban }.toSet()
            }
        return rows.map { row ->
            row.copy(
                status = if (row.fingerprint in existingFingerprints) RowStatus.DUPLICATE else row.status,
                unknownAccount = knownIbans.isNotEmpty() && row.accountIban !in knownIbans,
            )
        }
    }

    @PostMapping("/import-rows")
    suspend fun importRows(
        @RequestBody request: GenericCsvImportRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<ImportSuccessResponse> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val knownIbans =
            withContext(Dispatchers.IO) { getAccountsUseCase.getAccounts(organizationId) }
                .map { it.iban }
                .toSet()
        val rows = request.toImport
        val safeRows = if (knownIbans.isEmpty()) rows else rows.filter { it.accountIban in knownIbans }
        val transactions =
            safeRows.map { row ->
                Transaction(
                    category = row.category,
                    subcategory = row.subcategory,
                    group = row.group,
                    bookingDate = LocalDate.parse(row.date),
                    valueDate = LocalDate.parse(row.date),
                    accountingDate = LocalDate.parse(row.date),
                    amount = BigDecimal.valueOf(row.amount),
                    currency = row.currency,
                    accountIban = row.accountIban,
                    purpose = row.purpose,
                    counterpartyName = row.counterpartyName,
                    counterpartyIban = row.counterpartyIban,
                )
            }
        val accountNames = safeRows.associate { it.accountIban to it.accountIban }
        val count =
            withContext(Dispatchers.IO) {
                importTransactionsUseCase.importTransactions(
                    ImportTransactionsCommand(
                        transactions = transactions,
                        accountNames = accountNames,
                        organizationId = organizationId,
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
        return ResponseEntity.ok(ImportSuccessResponse(importedCount = count))
    }

    private suspend fun FilePart.readUtf8(): String {
        val bytes =
            DataBufferUtils
                .join(content())
                .map { buffer ->
                    val data = ByteArray(buffer.readableByteCount())
                    buffer.read(data)
                    DataBufferUtils.release(buffer)
                    data
                }.awaitSingle()
        return String(bytes, Charsets.UTF_8)
    }
}
