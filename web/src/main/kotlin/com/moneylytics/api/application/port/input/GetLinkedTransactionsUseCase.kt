package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

data class LinkedTransactionGroup(
    val transactions: List<Transaction>,
)

fun interface GetLinkedTransactionsUseCase {
    fun getLinkedGroups(userId: Long): List<LinkedTransactionGroup>
}
