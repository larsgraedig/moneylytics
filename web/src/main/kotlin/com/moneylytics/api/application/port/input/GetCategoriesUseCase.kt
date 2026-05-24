package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Category

fun interface GetCategoriesUseCase {
    fun getCategories(): List<Category>
}
