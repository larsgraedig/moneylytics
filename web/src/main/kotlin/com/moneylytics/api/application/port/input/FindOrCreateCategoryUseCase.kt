package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Category

fun interface FindOrCreateCategoryUseCase {
    fun findOrCreateCategory(
        name: String,
        subcategory: String?,
        group: String,
        organizationId: Long,
    ): Category
}
