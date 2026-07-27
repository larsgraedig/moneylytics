package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Collection

interface CreateCollectionUseCase {
    fun createCollection(
        collection: Collection,
        organizationId: Long,
    ): Collection
}
