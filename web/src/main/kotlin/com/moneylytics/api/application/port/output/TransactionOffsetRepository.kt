package com.moneylytics.api.application.port.output

import java.math.BigDecimal

interface TransactionOffsetRepository {
    fun create(command: CreateOffsetLinkCommand): OffsetLinkResult

    fun delete(
        linkId: Long,
        userId: Long,
    ): Boolean

    fun existsByPair(
        transactionAId: Long,
        transactionBId: Long,
    ): Boolean
}

data class CreateOffsetLinkCommand(
    val transactionAId: Long,
    val transactionBId: Long,
    val partialAmount: BigDecimal?,
)

data class OffsetLinkResult(
    val id: Long,
    val transactionAId: Long,
    val transactionBId: Long,
    val partialAmount: BigDecimal?,
)
