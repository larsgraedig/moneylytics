package com.moneylytics.api.application.port.input

import java.math.BigDecimal
import java.time.LocalDate

fun interface GetCategoryTotalsUseCase {
    fun getCategoryTotals(query: GetCategoryTotalsQuery): CategoryTotalsResponse
}

data class GetCategoryTotalsQuery(
    val from: LocalDate,
    val to: LocalDate,
    val organizationId: Long,
    val accountId: Long? = null,
    val category: String? = null,
    val categoryId: Long? = null,
)

data class CategoryTotalsResponse(
    val items: List<CategoryTotal>,
)

data class CategoryTotal(
    val name: String,
    val value: BigDecimal,
    val categoryId: Long? = null,
)
