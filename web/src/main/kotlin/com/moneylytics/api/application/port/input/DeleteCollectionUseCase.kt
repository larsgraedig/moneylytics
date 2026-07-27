package com.moneylytics.api.application.port.input

interface DeleteCollectionUseCase {
    fun deleteCollection(
        id: Long,
        organizationId: Long,
    )
}
