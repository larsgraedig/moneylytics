package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AllocationExceededException
import com.moneylytics.api.application.port.input.GetLinkedTransactionsUseCase
import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal

data class LinkTransactionRequest(
    val otherTransactionId: Long,
    val myAmount: BigDecimal? = null,
    val otherAmount: BigDecimal? = null,
    val targetGroupId: Long? = null,
    val forceNewGroup: Boolean = false,
)

data class LinkTransactionsResponse(
    val groupId: Long,
    val sourceTransaction: TransactionItem,
    val otherTransaction: TransactionItem,
)

data class AllocationErrorResponse(
    val transactionId: Long,
    val maxRemainingAmount: BigDecimal,
    val existingLinks: List<ExistingLinkDto>,
)

data class ExistingLinkDto(
    val linkId: Long,
    val linkedTransactionId: Long,
    val committedAmount: BigDecimal,
)

data class LinkedGroupResponse(
    val groups: List<LinkedGroupItem>,
)

data class LinkedGroupItem(
    val groupId: Long,
    val name: String?,
    val comment: String?,
    val transactions: List<TransactionItem>,
)

data class UpdateGroupMetaRequest(
    val name: String?,
    val comment: String?,
)

data class UpdateOffsetCommentRequest(
    val comment: String?,
)

@RestController
@RequestMapping("/transactions")
class TransactionOffsetController(
    private val manageTransactionOffsetUseCase: ManageTransactionOffsetUseCase,
    private val getLinkedTransactionsUseCase: GetLinkedTransactionsUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    companion object {
        private const val HTTP_UNPROCESSABLE_ENTITY = 422
    }

    @GetMapping("/linked")
    suspend fun getLinkedTransactions(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): LinkedGroupResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            LinkedGroupResponse(
                groups =
                    getLinkedTransactionsUseCase.getLinkedGroups(organizationId).map { group ->
                        LinkedGroupItem(
                            groupId = group.groupId,
                            name = group.name,
                            comment = group.comment,
                            transactions = group.transactions.map { it.toItem() },
                        )
                    },
            )
        }
    }

    @GetMapping("/linked/{groupId}")
    suspend fun getLinkedGroup(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<LinkedGroupItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            val group =
                getLinkedTransactionsUseCase.getLinkedGroup(groupId, organizationId)
                    ?: return@withContext ResponseEntity.notFound().build()
            ResponseEntity.ok(
                LinkedGroupItem(
                    groupId = group.groupId,
                    name = group.name,
                    comment = group.comment,
                    transactions = group.transactions.map { it.toItem() },
                ),
            )
        }
    }

    @PatchMapping("/linked/{groupId}")
    suspend fun updateGroupMeta(
        @PathVariable groupId: Long,
        @RequestBody request: UpdateGroupMetaRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Void> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            manageTransactionOffsetUseCase.updateGroupMeta(groupId, organizationId, request.name, request.comment)
        }
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/offsets")
    suspend fun linkTransaction(
        @PathVariable id: Long,
        @RequestBody request: LinkTransactionRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<*> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            val result =
                runCatching {
                    manageTransactionOffsetUseCase.linkTransactions(
                        LinkTransactionsCommand(
                            transactionId = id,
                            otherTransactionId = request.otherTransactionId,
                            myAmount = request.myAmount,
                            otherAmount = request.otherAmount,
                            organizationId = organizationId,
                            targetGroupId = request.targetGroupId,
                            forceNewGroup = request.forceNewGroup,
                        ),
                    )
                }.getOrElse { e ->
                    return@withContext when (e) {
                        is IllegalArgumentException -> ResponseEntity.notFound().build<Any>()
                        is IllegalStateException -> ResponseEntity.badRequest().build<Any>()
                        is AllocationExceededException ->
                            ResponseEntity.status(HTTP_UNPROCESSABLE_ENTITY).body(
                                AllocationErrorResponse(
                                    transactionId = e.transactionId,
                                    maxRemainingAmount = e.maxRemaining,
                                    existingLinks =
                                        e.existingLinks.map {
                                            ExistingLinkDto(it.linkId, it.linkedTransactionId, it.committedAmount)
                                        },
                                ),
                            )
                        else -> throw e
                    }
                }
            ResponseEntity.ok(
                LinkTransactionsResponse(
                    groupId = result.groupId,
                    sourceTransaction = result.sourceTransaction.toItem(),
                    otherTransaction = result.otherTransaction.toItem(),
                ),
            )
        }
    }

    @PatchMapping("/offsets/{linkId}/comment")
    suspend fun updateOffsetComment(
        @PathVariable linkId: Long,
        @RequestBody request: UpdateOffsetCommentRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Void> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            manageTransactionOffsetUseCase.updateOffsetComment(linkId, organizationId, request.comment)
        }
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{txId}/groups/{groupId}")
    suspend fun removeTransactionFromGroup(
        @PathVariable txId: Long,
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Void> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            manageTransactionOffsetUseCase.removeTransactionFromGroup(txId, groupId, organizationId)
        }
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/offsets/{linkId}")
    suspend fun unlinkTransaction(
        @PathVariable linkId: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Void> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val deleted =
            withContext(Dispatchers.IO) {
                manageTransactionOffsetUseCase.unlinkTransactions(linkId, organizationId)
            }
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}
