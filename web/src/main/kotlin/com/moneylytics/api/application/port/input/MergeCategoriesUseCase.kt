package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.CategoryMerge

data class MergeCategoriesCommand(
    val sourceId: Long,
    val targetId: Long,
    val organizationId: Long,
)

fun interface MergeCategoriesUseCase {
    fun mergeCategories(command: MergeCategoriesCommand): CategoryMerge
}
