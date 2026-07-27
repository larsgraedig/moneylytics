package com.moneylytics.api.application.port.input

import java.math.BigDecimal
import java.time.LocalDate

fun interface GetBurnRateUseCase {
    fun getBurnRate(query: GetBurnRateQuery): BurnRateResponse
}

data class GetBurnRateQuery(
    val from: LocalDate,
    val to: LocalDate,
    val organizationId: Long,
    val accountIban: String? = null,
    val rollingWindow: Int = 7,
)

data class BurnRateResponse(
    val points: List<BurnRatePoint>,
    val totalExpenses: BigDecimal,
    val totalIncome: BigDecimal,
    val avgPerDay: BigDecimal,
)

data class BurnRatePoint(
    val date: String,
    val expenses: BigDecimal,
    val rollingAvg: BigDecimal,
    val cumulative: BigDecimal,
    val cumulativeIncome: BigDecimal,
)
