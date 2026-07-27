package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction
import java.math.BigDecimal

data class LinkTransactionResult(
    val groupId: Long,
    val sourceTransaction: Transaction,
    val otherTransaction: Transaction,
)

interface ManageTransactionOffsetUseCase {
    fun linkTransactions(command: LinkTransactionsCommand): LinkTransactionResult

    fun unlinkTransactions(
        linkId: Long,
        organizationId: Long,
    ): Boolean

    fun updateGroupMeta(
        groupId: Long,
        organizationId: Long,
        name: String?,
        comment: String?,
    )

    fun updateOffsetComment(
        linkId: Long,
        organizationId: Long,
        comment: String?,
    )

    fun removeTransactionFromGroup(
        txId: Long,
        groupId: Long,
        organizationId: Long,
    )
}

data class LinkTransactionsCommand(
    val transactionId: Long,
    val otherTransactionId: Long,
    val myAmount: BigDecimal?,
    val otherAmount: BigDecimal?,
    val organizationId: Long,
    val targetGroupId: Long? = null,
    val forceNewGroup: Boolean = false,
)
