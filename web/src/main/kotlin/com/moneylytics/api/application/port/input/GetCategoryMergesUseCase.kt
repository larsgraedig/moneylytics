package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.CategoryMerge

fun interface GetCategoryMergesUseCase {
    fun getMerges(organizationId: Long): List<CategoryMerge>
}
