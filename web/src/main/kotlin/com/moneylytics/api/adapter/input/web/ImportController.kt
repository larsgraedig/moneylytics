package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetImportsUseCase
import com.moneylytics.api.application.port.input.RejectImportFileResult
import com.moneylytics.api.application.port.input.RejectImportFileUseCase
import com.moneylytics.api.application.port.input.RejectImportResult
import com.moneylytics.api.application.port.input.RejectImportUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.domain.ImportFile
import com.moneylytics.api.domain.TransactionImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

data class ImportFileDto(
    val id: Long,
    val filename: String,
    val checksum: String,
    val fileType: String,
    val transactionCount: Int,
    val status: String,
)

data class TransactionImportDto(
    val id: Long,
    val importedAt: String,
    val transactionCount: Int,
    val status: String,
    val files: List<ImportFileDto>,
)

data class RejectImportSuccessResponse(
    val rejectedCount: Int,
)

data class RejectImportFailureResponse(
    val blocked: List<BlockedTransactionDto>,
)

data class BlockedTransactionDto(
    val transactionId: Long,
    val reasons: List<String>,
)

data class RejectImportRequest(
    val force: Boolean = false,
)

@RestController
@RequestMapping("/imports")
class ImportController(
    private val getImportsUseCase: GetImportsUseCase,
    private val rejectImportUseCase: RejectImportUseCase,
    private val rejectImportFileUseCase: RejectImportFileUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    @GetMapping
    suspend fun listImports(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): List<TransactionImportDto> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            getImportsUseCase.getImports(organizationId).map { it.toDto() }
        }
    }

    @PostMapping("/{id}/reject")
    suspend fun rejectImport(
        @PathVariable id: Long,
        @RequestBody(required = false) request: RejectImportRequest?,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<out Any> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            when (val result = rejectImportUseCase.rejectImport(id, organizationId, request?.force ?: false)) {
                is RejectImportResult.Success ->
                    ResponseEntity.ok(RejectImportSuccessResponse(rejectedCount = result.rejectedCount))

                is RejectImportResult.Failure ->
                    ResponseEntity
                        .status(422)
                        .body(
                            RejectImportFailureResponse(
                                blocked = result.blockedTransactions.map { BlockedTransactionDto(it.transactionId, it.reasons) },
                            ),
                        )
            }
        }
    }

    @PostMapping("/{id}/files/{fileId}/reject")
    suspend fun rejectImportFile(
        @PathVariable id: Long,
        @PathVariable fileId: Long,
        @RequestBody(required = false) request: RejectImportRequest?,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<out Any> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            when (val result = rejectImportFileUseCase.rejectImportFile(fileId, id, organizationId, request?.force ?: false)) {
                is RejectImportFileResult.Success ->
                    ResponseEntity.ok(RejectImportSuccessResponse(rejectedCount = result.rejectedCount))

                is RejectImportFileResult.Failure ->
                    ResponseEntity
                        .status(422)
                        .body(
                            RejectImportFailureResponse(
                                blocked = result.blockedTransactions.map { BlockedTransactionDto(it.transactionId, it.reasons) },
                            ),
                        )
            }
        }
    }

    private fun TransactionImport.toDto() =
        TransactionImportDto(
            id = requireNotNull(id),
            importedAt = importedAt.toString(),
            transactionCount = transactionCount,
            status = status.name,
            files = files.map { it.toDto() },
        )

    private fun ImportFile.toDto() =
        ImportFileDto(
            id = requireNotNull(id),
            filename = filename,
            checksum = checksum,
            fileType = fileType.name,
            transactionCount = transactionCount,
            status = status.name,
        )
}
