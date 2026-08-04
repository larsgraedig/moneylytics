package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Category

fun interface FindOrCreateCategoryUseCase {
    fun findOrCreateCategory(
        path: List<String>,
        organizationId: Long,
    ): Category
}
