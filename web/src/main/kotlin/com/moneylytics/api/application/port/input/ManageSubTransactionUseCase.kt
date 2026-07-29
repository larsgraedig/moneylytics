package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.SplitItem
import com.moneylytics.api.domain.SubTransactionGroup
import java.time.LocalDate

data class SplitTransactionCommand(
    val transactionId: Long,
    val splits: List<SplitItem>,
    val organizationId: Long,
)

data class MergeTransactionsCommand(
    val transactionIds: List<Long>,
    val accountingDate: LocalDate,
    val name: String?,
    val comment: String?,
    val organizationId: Long,
)

interface ManageSubTransactionUseCase {
    fun splitTransaction(command: SplitTransactionCommand): SubTransactionGroup

    fun unsplitTransaction(
        transactionId: Long,
        organizationId: Long,
    )

    fun mergeTransactions(command: MergeTransactionsCommand): SubTransactionGroup

    fun addToMerge(
        parentId: Long,
        transactionId: Long,
        organizationId: Long,
    ): SubTransactionGroup

    fun removeFromMerge(
        transactionId: Long,
        organizationId: Long,
    ): SubTransactionGroup

    fun unmergeTransactions(
        parentId: Long,
        organizationId: Long,
    )
}

interface GetSubTransactionGroupUseCase {
    fun getGroup(
        transactionId: Long,
        organizationId: Long,
    ): SubTransactionGroup?
}
