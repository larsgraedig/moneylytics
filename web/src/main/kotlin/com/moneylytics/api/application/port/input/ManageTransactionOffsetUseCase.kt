package com.moneylytics.api.application.port.input

import com.moneylytics.api.application.port.output.OffsetLinkResult
import java.math.BigDecimal

interface ManageTransactionOffsetUseCase {
    fun linkTransactions(command: LinkTransactionsCommand): OffsetLinkResult

    fun unlinkTransactions(
        linkId: Long,
        userId: Long,
    ): Boolean

    fun updateGroupMeta(
        groupId: Long,
        userId: Long,
        name: String?,
        comment: String?,
    )

    fun updateOffsetComment(
        linkId: Long,
        userId: Long,
        comment: String?,
    )

    fun removeTransactionFromGroup(
        txId: Long,
        groupId: Long,
        userId: Long,
    )
}

data class LinkTransactionsCommand(
    val transactionId: Long,
    val otherTransactionId: Long,
    val myAmount: BigDecimal?,
    val otherAmount: BigDecimal?,
    val userId: Long,
    val targetGroupId: Long? = null,
    val forceNewGroup: Boolean = false,
)
