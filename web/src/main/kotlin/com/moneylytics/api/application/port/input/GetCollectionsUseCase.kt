package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

data class CollectionWithTransactions(
    val id: Long,
    val name: String,
    val note: String?,
    val transactions: List<Transaction>,
)

interface GetCollectionsUseCase {
    fun getCollections(userId: Long): List<CollectionWithTransactions>
}
