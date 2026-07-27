package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Category

fun interface GetCategoriesUseCase {
    fun getCategories(organizationId: Long): List<Category>
}
