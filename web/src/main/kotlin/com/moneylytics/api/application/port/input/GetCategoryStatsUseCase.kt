package com.moneylytics.api.application.port.input

import java.time.LocalDate

data class GetCategoryStatsQuery(
    val organizationId: Long,
    val from: LocalDate,
    val to: LocalDate,
    val iban: String? = null,
)

data class CategoryStatItem(
    val categoryId: Long,
    val totalCount: Long,
    val periodCount: Long,
)

fun interface GetCategoryStatsUseCase {
    fun getCategoryStats(query: GetCategoryStatsQuery): List<CategoryStatItem>
}
