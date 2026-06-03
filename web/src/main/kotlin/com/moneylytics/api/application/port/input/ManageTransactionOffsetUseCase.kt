package com.moneylytics.api.application.port.input

import com.moneylytics.api.application.port.output.OffsetLinkResult
import java.math.BigDecimal

interface ManageTransactionOffsetUseCase {
    fun linkTransactions(command: LinkTransactionsCommand): OffsetLinkResult

    fun unlinkTransactions(
        linkId: Long,
        userId: Long,
    ): Boolean
}

data class LinkTransactionsCommand(
    val transactionId: Long,
    val otherTransactionId: Long,
    val partialAmount: BigDecimal?,
    val userId: Long,
)
