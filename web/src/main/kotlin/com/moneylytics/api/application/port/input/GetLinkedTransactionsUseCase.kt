package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

data class LinkedTransactionGroup(
    val groupKey: Long,
    val name: String?,
    val comment: String?,
    val transactions: List<Transaction>,
)

fun interface GetLinkedTransactionsUseCase {
    fun getLinkedGroups(userId: Long): List<LinkedTransactionGroup>
}

fun interface UpdateOffsetGroupMetaUseCase {
    fun updateGroupMeta(
        userId: Long,
        groupKey: Long,
        name: String?,
        comment: String?,
    )
}
