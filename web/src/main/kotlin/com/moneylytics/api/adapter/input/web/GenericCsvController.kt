package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.adapter.output.persistence.CsvProfilePersistenceAdapter
import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
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
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/transactions/csv")
class GenericCsvController(
    private val detector: GenericCsvDetector,
    private val parser: GenericCsvParser,
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val checkDuplicatesUseCase: CheckDuplicatesUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
    private val csvProfileAdapter: CsvProfilePersistenceAdapter,
) {
    @PostMapping("/detect", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun detect(
        @RequestPart("file") filePart: FilePart,
        @AuthenticationPrincipal principal: UserDetails,
    ): CsvDetectionResult {
        val content = filePart.readUtf8()
        val detection = withContext(Dispatchers.Default) { detector.detect(content) }
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val savedMapping =
            withContext(Dispatchers.IO) {
                csvProfileAdapter.findMapping(userId, detection.fingerprint)
            }
        return detection.result.copy(savedMapping = savedMapping)
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun importGeneric(
        @RequestPart("file") filePart: FilePart,
        @RequestPart("mapping") mapping: GenericCsvMapping,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<ImportSuccessResponse> {
        val content = filePart.readUtf8()
        val (transactions, accountNames) = withContext(Dispatchers.Default) { parser.parse(content, mapping) }
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val count =
            importTransactionsUseCase.importTransactions(
                ImportTransactionsCommand(
                    transactions = transactions,
                    accountNames = accountNames,
                    userId = userId,
                ),
            )
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
        withContext(Dispatchers.IO) { csvProfileAdapter.saveMapping(userId, fingerprint, mapping) }
        return ResponseEntity.ok(ImportSuccessResponse(importedCount = count))
    }

    @PostMapping("/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun preview(
        @RequestPart("file") filePart: FilePart,
        @RequestPart("mapping") mapping: GenericCsvMapping,
        @AuthenticationPrincipal principal: UserDetails,
    ): List<GenericCsvPreviewRow> {
        val content = filePart.readUtf8()
        val rows = withContext(Dispatchers.Default) { parser.preview(content, mapping) }
        if (rows.isEmpty()) return rows

        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val allFingerprints = rows.map { it.fingerprint }.toSet()
        val (existingFingerprints, knownIbans) =
            withContext(Dispatchers.IO) {
                checkDuplicatesUseCase.findExistingFingerprints(allFingerprints, userId) to
                    getAccountsUseCase.getAccounts(userId).map { it.iban }.toSet()
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
        @RequestBody rows: List<GenericRowToImport>,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<ImportSuccessResponse> {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val knownIbans =
            withContext(Dispatchers.IO) { getAccountsUseCase.getAccounts(userId) }
                .map { it.iban }
                .toSet()
        val safeRows = if (knownIbans.isEmpty()) rows else rows.filter { it.accountIban in knownIbans }
        val transactions =
            safeRows.map { row ->
                Transaction(
                    category = row.category,
                    subcategory = row.subcategory,
                    bookingDate = LocalDate.parse(row.date),
                    valueDate = LocalDate.parse(row.date),
                    accountingDate = LocalDate.parse(row.date),
                    amount = BigDecimal.valueOf(row.amount),
                    currency = row.currency,
                    accountIban = row.accountIban,
                    purpose = row.purpose,
                )
            }
        val accountNames = safeRows.associate { it.accountIban to it.accountIban }
        val count =
            importTransactionsUseCase.importTransactions(
                ImportTransactionsCommand(
                    transactions = transactions,
                    accountNames = accountNames,
                    userId = userId,
                ),
            )
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
