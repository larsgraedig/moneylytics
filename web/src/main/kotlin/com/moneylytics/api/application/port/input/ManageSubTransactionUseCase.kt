package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.SplitItem
import com.moneylytics.api.domain.SubTransactionGroup
import com.moneylytics.api.domain.Transaction
import java.math.BigDecimal
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

data class CreateVirtualTransactionCommand(
    val amount: BigDecimal,
    val currency: String,
    val accountIban: String,
    val accountingDate: LocalDate,
    val categoryId: Long?,
    val counterpartyName: String?,
    val purpose: String?,
    val organizationId: Long,
)

data class UpdateVirtualTransactionCommand(
    val transactionId: Long,
    val amount: BigDecimal,
    val accountIban: String,
    val accountingDate: LocalDate,
    val categoryId: Long?,
    val counterpartyName: String?,
    val purpose: String?,
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

    fun createVirtualTransaction(command: CreateVirtualTransactionCommand): Transaction

    fun updateVirtualTransaction(command: UpdateVirtualTransactionCommand): Transaction

    fun deleteVirtualTransaction(
        transactionId: Long,
        organizationId: Long,
    )
}

interface GetSubTransactionGroupUseCase {
    fun getGroup(
        transactionId: Long,
        organizationId: Long,
    ): SubTransactionGroup?
}
