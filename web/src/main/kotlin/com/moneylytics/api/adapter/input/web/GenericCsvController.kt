package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.adapter.output.persistence.TransactionImportCsvProfilePersistenceAdapter
import com.moneylytics.api.adapter.output.persistence.TransactionImportPreviewSessionPersistenceAdapter
import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ImportFileSpec
import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.output.CategoryClassifier
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
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val PREVIEW_SESSION_TTL_HOURS = 24L

@RestController
@RequestMapping("/transactions/csv")
class GenericCsvController(
    private val detector: GenericCsvDetector,
    private val parser: GenericCsvParser,
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val checkDuplicatesUseCase: CheckDuplicatesUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
    private val csvProfileAdapter: TransactionImportCsvProfilePersistenceAdapter,
    private val importPreviewSessionAdapter: TransactionImportPreviewSessionPersistenceAdapter,
    private val categoryClassifier: CategoryClassifier,
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
        val bytes = filePart.readBytes()
        val content = String(bytes, Charsets.UTF_8)
        val checksum = sha256(bytes)
        val (transactions, accountNames) = withContext(Dispatchers.Default) { parser.parse(content, mapping) }
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val importResult =
            withContext(Dispatchers.IO) {
                importTransactionsUseCase.importTransactions(
                    ImportTransactionsCommand(
                        transactions = transactions,
                        accountNames = accountNames,
                        organizationId = organizationId,
                        files =
                            listOf(
                                ImportFileSpec(
                                    filename = filePart.filename(),
                                    checksum = checksum,
                                    fileType = ImportFileType.GENERIC,
                                    fingerprints = emptyList(),
                                ),
                            ),
                    ),
                )
            }
        val count = importResult.importedCount
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
        return ResponseEntity.ok(ImportSuccessResponse(importedCount = count, importId = importResult.importId))
    }

    @PostMapping("/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun preview(
        @RequestPart("mapping") mapping: GenericCsvMapping,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): GenericCsvPreviewResponse {
        val parts = exchange.multipartData.awaitSingle()
        val fileParts = parts["files"]?.filterIsInstance<FilePart>() ?: emptyList()
        if (fileParts.isEmpty()) {
            val token = UUID.randomUUID()
            withContext(Dispatchers.IO) {
                importPreviewSessionAdapter.save(token, emptyList(), LocalDateTime.now().plusHours(PREVIEW_SESSION_TTL_HOURS))
            }
            return GenericCsvPreviewResponse(rows = emptyList(), previewToken = token)
        }

        val allRows = mutableListOf<GenericCsvPreviewRow>()
        var nextRowIndex = 0
        for (filePart in fileParts) {
            val content = filePart.readUtf8()
            val rows = withContext(Dispatchers.Default) { parser.preview(content, mapping) }
            rows.mapIndexedTo(allRows) { idx, row ->
                row.copy(rowIndex = nextRowIndex + idx, sourceFilename = filePart.filename())
            }
            nextRowIndex += rows.size
        }

        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val allFingerprints = allRows.map { it.fingerprint }.toSet()
        val (existingFingerprints, knownIbans, suggestions) =
            withContext(Dispatchers.IO) {
                val fingerprints = checkDuplicatesUseCase.findExistingFingerprints(allFingerprints, organizationId)
                val ibans = getAccountsUseCase.getAccounts(organizationId).map { it.iban }.toSet()
                val features =
                    allRows.map { row ->
                        CategoryClassifierFeatures(
                            purpose = row.purpose,
                            counterpartyName = row.counterpartyName,
                            counterpartyIban = row.counterpartyIban,
                            amount = java.math.BigDecimal.valueOf(row.amount),
                        )
                    }
                val suggested = categoryClassifier.suggestAll(organizationId, features)
                Triple(fingerprints, ibans, allRows.zip(suggested).associate { (row, catId) -> row.fingerprint to catId })
            }
        val finalRows =
            allRows.map { row ->
                val isDuplicate = row.fingerprint in existingFingerprints
                row.copy(
                    status = if (isDuplicate) RowStatus.DUPLICATE else row.status,
                    unknownAccount = knownIbans.isNotEmpty() && row.accountIban !in knownIbans,
                    suggestedCategoryId = if (!isDuplicate) suggestions[row.fingerprint] else null,
                )
            }

        val previewToken = UUID.randomUUID()
        withContext(Dispatchers.IO) {
            importPreviewSessionAdapter.save(previewToken, finalRows, LocalDateTime.now().plusHours(PREVIEW_SESSION_TTL_HOURS))
        }
        return GenericCsvPreviewResponse(rows = finalRows, previewToken = previewToken)
    }

    @PostMapping("/import-rows")
    suspend fun importRows(
        @RequestBody request: GenericCsvImportByTokenRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<ImportSuccessResponse> {
        val sessionRows =
            withContext(Dispatchers.IO) { importPreviewSessionAdapter.load(request.previewToken, GenericCsvPreviewRow::class.java) }
                ?: return ResponseEntity.notFound().build()

        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val knownIbans =
            withContext(Dispatchers.IO) { getAccountsUseCase.getAccounts(organizationId) }
                .map { it.iban }
                .toSet()

        val excludedSet = request.excludedRowIndices.toSet()
        val selectedRows = sessionRows.filter { it.rowIndex !in excludedSet }
        val safeRows = if (knownIbans.isEmpty()) selectedRows else selectedRows.filter { it.accountIban in knownIbans }

        val transactions =
            safeRows.map { row ->
                Transaction(
                    category = row.mappedCategory ?: "",
                    subcategory = row.mappedSubcategory,
                    group = row.mappedGroup ?: "",
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
        val checksum =
            sha256(
                safeRows
                    .map { "${it.date}|${it.amount}|${it.accountIban}" }
                    .sorted()
                    .joinToString(",")
                    .toByteArray(Charsets.UTF_8),
            )
        val importResult =
            withContext(Dispatchers.IO) {
                importTransactionsUseCase.importTransactions(
                    ImportTransactionsCommand(
                        transactions = transactions,
                        accountNames = accountNames,
                        organizationId = organizationId,
                        files =
                            listOf(
                                ImportFileSpec(
                                    filename = "api-import",
                                    checksum = checksum,
                                    fileType = ImportFileType.GENERIC,
                                    fingerprints = emptyList(),
                                ),
                            ),
                    ),
                )
            }
        request.mappingsToSave.forEach { (fingerprint, mapping) ->
            withContext(Dispatchers.IO) { csvProfileAdapter.saveMapping(organizationId, fingerprint, mapping) }
        }
        withContext(Dispatchers.IO) { importPreviewSessionAdapter.delete(request.previewToken) }
        return ResponseEntity.ok(ImportSuccessResponse(importedCount = importResult.importedCount, importId = importResult.importId))
    }

    private suspend fun FilePart.readBytes(): ByteArray =
        DataBufferUtils
            .join(content())
            .map { buffer ->
                val data = ByteArray(buffer.readableByteCount())
                buffer.read(data)
                DataBufferUtils.release(buffer)
                data
            }.awaitSingle()

    private suspend fun FilePart.readUtf8(): String = String(readBytes(), Charsets.UTF_8)
}
