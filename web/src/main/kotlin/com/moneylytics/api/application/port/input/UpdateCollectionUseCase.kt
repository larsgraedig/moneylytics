package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Collection

interface UpdateCollectionUseCase {
    fun updateCollection(
        collection: Collection,
        organizationId: Long,
    ): Collection
}
