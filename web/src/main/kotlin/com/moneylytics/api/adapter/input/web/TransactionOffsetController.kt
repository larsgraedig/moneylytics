package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AllocationExceededException
import com.moneylytics.api.application.port.input.GetLinkedTransactionsUseCase
import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.output.OffsetLinkResult
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
import java.math.BigDecimal

data class LinkTransactionRequest(
    val otherTransactionId: Long,
    val myAmount: BigDecimal? = null,
    val otherAmount: BigDecimal? = null,
    val targetGroupId: Long? = null,
    val forceNewGroup: Boolean = false,
)

data class OffsetLinkResponse(
    val id: Long?,
    val transactionAId: Long,
    val transactionBId: Long,
    val amountA: BigDecimal?,
    val amountB: BigDecimal?,
    val groupId: Long,
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
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @GetMapping("/linked")
    suspend fun getLinkedTransactions(
        @AuthenticationPrincipal principal: UserDetails,
    ): LinkedGroupResponse =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            LinkedGroupResponse(
                groups =
                    getLinkedTransactionsUseCase.getLinkedGroups(userId).map { group ->
                        LinkedGroupItem(
                            groupId = group.groupId,
                            name = group.name,
                            comment = group.comment,
                            transactions = group.transactions.map { it.toItem() },
                        )
                    },
            )
        }

    @GetMapping("/linked/{groupId}")
    suspend fun getLinkedGroup(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<LinkedGroupItem> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            val group =
                getLinkedTransactionsUseCase.getLinkedGroup(groupId, userId)
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

    @PatchMapping("/linked/{groupId}")
    suspend fun updateGroupMeta(
        @PathVariable groupId: Long,
        @RequestBody request: UpdateGroupMetaRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Void> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            manageTransactionOffsetUseCase.updateGroupMeta(groupId, userId, request.name, request.comment)
            ResponseEntity.noContent().build()
        }

    @PostMapping("/{id}/offsets")
    suspend fun linkTransaction(
        @PathVariable id: Long,
        @RequestBody request: LinkTransactionRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<*> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            val result =
                runCatching {
                    manageTransactionOffsetUseCase.linkTransactions(
                        LinkTransactionsCommand(
                            transactionId = id,
                            otherTransactionId = request.otherTransactionId,
                            myAmount = request.myAmount,
                            otherAmount = request.otherAmount,
                            userId = userId,
                            targetGroupId = request.targetGroupId,
                            forceNewGroup = request.forceNewGroup,
                        ),
                    )
                }.getOrElse { e ->
                    return@withContext when (e) {
                        is IllegalArgumentException -> ResponseEntity.notFound().build<Any>()
                        is IllegalStateException -> ResponseEntity.badRequest().build<Any>()
                        is AllocationExceededException ->
                            ResponseEntity.status(422).body(
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
            ResponseEntity.ok(result.toResponse())
        }

    @PatchMapping("/offsets/{linkId}/comment")
    suspend fun updateOffsetComment(
        @PathVariable linkId: Long,
        @RequestBody request: UpdateOffsetCommentRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Void> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            manageTransactionOffsetUseCase.updateOffsetComment(linkId, userId, request.comment)
            ResponseEntity.noContent().build()
        }

    @DeleteMapping("/{txId}/groups/{groupId}")
    suspend fun removeTransactionFromGroup(
        @PathVariable txId: Long,
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Void> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            manageTransactionOffsetUseCase.removeTransactionFromGroup(txId, groupId, userId)
            ResponseEntity.noContent().build()
        }

    @DeleteMapping("/offsets/{linkId}")
    suspend fun unlinkTransaction(
        @PathVariable linkId: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Void> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            val deleted = manageTransactionOffsetUseCase.unlinkTransactions(linkId, userId)
            if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
        }

    private fun OffsetLinkResult.toResponse() =
        OffsetLinkResponse(
            id = id,
            transactionAId = transactionAId,
            transactionBId = transactionBId,
            amountA = amountA,
            amountB = amountB,
            groupId = groupId,
        )
}
