package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.output.OffsetLinkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

data class LinkTransactionRequest(
    val otherTransactionId: Long,
    val partialAmount: BigDecimal? = null,
)

data class OffsetLinkResponse(
    val id: Long,
    val transactionAId: Long,
    val transactionBId: Long,
    val partialAmount: BigDecimal?,
)

@RestController
@RequestMapping("/transactions")
class TransactionOffsetController(
    private val manageTransactionOffsetUseCase: ManageTransactionOffsetUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @PostMapping("/{id}/offsets")
    suspend fun linkTransaction(
        @PathVariable id: Long,
        @RequestBody request: LinkTransactionRequest,
        @RequestHeader("X-User-Id") externalId: String,
    ): ResponseEntity<OffsetLinkResponse> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(externalId)
            val result =
                runCatching {
                    manageTransactionOffsetUseCase.linkTransactions(
                        LinkTransactionsCommand(
                            transactionId = id,
                            otherTransactionId = request.otherTransactionId,
                            partialAmount = request.partialAmount,
                            userId = userId,
                        ),
                    )
                }.getOrElse { e ->
                    return@withContext when (e) {
                        is IllegalArgumentException -> ResponseEntity.notFound().build()
                        is IllegalStateException -> ResponseEntity.badRequest().build()
                        else -> throw e
                    }
                }
            ResponseEntity.ok(result.toResponse())
        }

    @DeleteMapping("/offsets/{linkId}")
    suspend fun unlinkTransaction(
        @PathVariable linkId: Long,
        @RequestHeader("X-User-Id") externalId: String,
    ): ResponseEntity<Void> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(externalId)
            val deleted = manageTransactionOffsetUseCase.unlinkTransactions(linkId, userId)
            if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
        }

    private fun OffsetLinkResult.toResponse() =
        OffsetLinkResponse(
            id = id,
            transactionAId = transactionAId,
            transactionBId = transactionBId,
            partialAmount = partialAmount,
        )
}
