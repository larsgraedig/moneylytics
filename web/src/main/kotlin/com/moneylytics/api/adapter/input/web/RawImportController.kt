package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.FindIgnoredFingerprintsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.SaveCategoriesUseCase
import com.moneylytics.api.application.port.input.UpdateIgnoredTransactionsUseCase
import com.moneylytics.api.domain.Category
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/transactions")
class RawImportController(
    private val mlpRawCsvParser: MlpRawCsvParser,
    private val checkDuplicatesUseCase: CheckDuplicatesUseCase,
    private val findIgnoredFingerprintsUseCase: FindIgnoredFingerprintsUseCase,
    private val updateIgnoredTransactionsUseCase: UpdateIgnoredTransactionsUseCase,
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val saveCategoriesUseCase: SaveCategoriesUseCase,
) {
    @PostMapping("/raw/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun preview(
        @RequestPart("file") filePart: FilePart,
    ): ResponseEntity<out Any> {
        val csvContent = filePart.readContent()

        return when (val result = mlpRawCsvParser.parse(csvContent)) {
            is MlpRawParseResult.FormatError ->
                ResponseEntity
                    .unprocessableContent()
                    .body(mapOf("error" to result.message))

            is MlpRawParseResult.Success -> {
                val validRows = result.rows.filter { it.isValid }
                val rowFingerprints = assignFingerprints(validRows)

                val allFingerprints = rowFingerprints.values.toSet()
                val existingFingerprints =
                    if (allFingerprints.isEmpty()) emptySet() else checkDuplicatesUseCase.findExistingFingerprints(allFingerprints)
                val ignoredFingerprints =
                    if (allFingerprints.isEmpty()) emptySet() else findIgnoredFingerprintsUseCase.findIgnoredFingerprints(allFingerprints)

                val response =
                    RawPreviewResponse(
                        rows =
                            result.rows.map { row ->
                                val fp = rowFingerprints[row.rowNumber]
                                val status =
                                    when {
                                        !row.isValid -> RowStatus.INVALID
                                        fp in existingFingerprints -> RowStatus.DUPLICATE
                                        fp in ignoredFingerprints -> RowStatus.PREVIOUSLY_IGNORED
                                        else -> RowStatus.NEW
                                    }
                                row.toPreviewRow(status, fp)
                            },
                    )
                ResponseEntity.ok(response)
            }
        }
    }

    @PostMapping("/raw/import")
    suspend fun importRaw(
        @RequestBody request: ImportRawRequest,
    ): ResponseEntity<out Any> {
        updateIgnoredTransactionsUseCase.update(
            toIgnore = request.toIgnore,
            toUnignore = request.toImport.map { it.fingerprint },
        )

        val categories =
            request.toImport
                .map { Category(name = it.category, subcategory = it.subcategory) }
                .distinct()
        saveCategoriesUseCase.saveCategories(categories)

        val transactions =
            request.toImport.map { row ->
                Transaction(
                    category = row.category,
                    subcategory = row.subcategory,
                    bookingDate = LocalDate.parse(row.bookingDate, DateTimeFormatter.ISO_LOCAL_DATE),
                    valueDate = LocalDate.parse(row.valueDate, DateTimeFormatter.ISO_LOCAL_DATE),
                    amount = row.amount,
                    currency = row.currency,
                    accountIban = request.accountIban,
                )
            }

        val importedCount =
            importTransactionsUseCase.importTransactions(
                ImportTransactionsCommand(
                    transactions = transactions,
                    accountNames = mapOf(request.accountIban to request.accountName),
                ),
            )

        return ResponseEntity.ok(ImportSuccessResponse(importedCount = importedCount))
    }

    private fun assignFingerprints(rows: List<ParsedRawRow>): Map<Int, String> {
        val counts = mutableMapOf<String, Int>()
        return rows.associate { row ->
            val raw =
                "${row.accountIban}|${row.bookingDate}|${row.valueDate}|" +
                    "${row.amount!!.stripTrailingZeros().toPlainString()}|${row.currency}"
            val n = counts.merge(raw, 1, Int::plus)!!
            val fp = sha256(if (n == 1) raw else "$raw:${n - 1}")
            row.rowNumber to fp
        }
    }

    private fun sha256(raw: String) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun ParsedRawRow.toPreviewRow(
        status: RowStatus,
        fingerprint: String?,
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
    )

    private suspend fun FilePart.readContent(): String {
        val bytes =
            DataBufferUtils
                .join(content())
                .map { buffer ->
                    val content = ByteArray(buffer.readableByteCount())
                    buffer.read(content)
                    DataBufferUtils.release(buffer)
                    content
                }.awaitSingle()
        return String(bytes, Charsets.UTF_8)
    }
}
