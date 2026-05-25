package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/transactions")
class TransactionImportController(
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val csvTransactionParser: CsvTransactionParser,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun importTransactions(
        @RequestPart("file") filePart: FilePart,
        @RequestHeader("X-User-Id") externalId: String,
    ): ResponseEntity<out Any> {
        val bytes =
            DataBufferUtils
                .join(filePart.content())
                .map { buffer ->
                    val content = ByteArray(buffer.readableByteCount())
                    buffer.read(content)
                    DataBufferUtils.release(buffer)
                    content
                }.awaitSingle()

        val csvContent = String(bytes, Charsets.UTF_8)

        return when (val result = csvTransactionParser.parse(csvContent)) {
            is CsvParseResult.Invalid -> {
                ResponseEntity
                    .unprocessableContent()
                    .body(CsvValidationErrorsResponse(errors = result.errors))
            }

            is CsvParseResult.Valid -> {
                val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(externalId) }
                val importedCount =
                    importTransactionsUseCase.importTransactions(
                        ImportTransactionsCommand(
                            transactions = result.transactions,
                            accountNames = result.accountNames,
                            userId = userId,
                        ),
                    )
                ResponseEntity.ok(ImportSuccessResponse(importedCount = importedCount))
            }
        }
    }
}
