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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/transactions/csv")
class GenericCsvController(
    private val detector: GenericCsvDetector,
    private val parser: GenericCsvParser,
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @PostMapping("/detect", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun detect(
        @RequestPart("file") filePart: FilePart,
        @AuthenticationPrincipal principal: UserDetails,
    ): CsvDetectionResult {
        val content = filePart.readUtf8()
        return withContext(Dispatchers.Default) { detector.detect(content) }
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
