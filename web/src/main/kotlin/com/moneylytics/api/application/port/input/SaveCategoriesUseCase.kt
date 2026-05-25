package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Category

fun interface SaveCategoriesUseCase {
    fun saveCategories(
        categories: List<Category>,
        userId: Long,
    )
}
