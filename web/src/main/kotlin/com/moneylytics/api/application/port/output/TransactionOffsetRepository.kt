package com.moneylytics.api.application.port.output

import java.math.BigDecimal

interface TransactionOffsetRepository {
    fun create(command: CreateOffsetLinkCommand): OffsetLinkResult

    fun delete(
        linkId: Long,
        organizationId: Long,
    ): DeletedOffsetLink?

    fun existsByPair(
        transactionAId: Long,
        transactionBId: Long,
    ): Boolean

    fun findLinksForGroup(groupId: Long): List<Pair<Long, Long>>

    fun updateLinksGroupId(
        fromGroupId: Long,
        toGroupId: Long,
        memberIds: Set<Long>,
    )

    fun updateComment(
        linkId: Long,
        organizationId: Long,
        comment: String?,
    )

    fun deleteByTxAndGroupId(
        txId: Long,
        groupId: Long,
        organizationId: Long,
    )
}

data class CreateOffsetLinkCommand(
    val transactionAId: Long,
    val transactionBId: Long,
    val amountA: BigDecimal?,
    val amountB: BigDecimal?,
    val groupId: Long,
)

data class OffsetLinkResult(
    val id: Long?,
    val transactionAId: Long,
    val transactionBId: Long,
    val amountA: BigDecimal?,
    val amountB: BigDecimal?,
    val groupId: Long,
)

data class DeletedOffsetLink(
    val groupId: Long?,
)
