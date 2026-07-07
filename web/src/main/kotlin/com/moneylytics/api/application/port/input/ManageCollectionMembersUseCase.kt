package com.moneylytics.api.application.port.input

interface ManageCollectionMembersUseCase {
    fun addTransaction(
        collectionId: Long,
        transactionId: Long,
        userId: Long,
    )

    fun removeTransaction(
        collectionId: Long,
        transactionId: Long,
        userId: Long,
    )
}
