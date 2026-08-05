package com.moneylytics.api.application.port.input

import java.math.BigDecimal
import java.time.LocalDate

fun interface GetCashflowUseCase {
    fun getCashflow(query: GetCashflowQuery): CashflowResponse
}

data class GetCashflowQuery(
    val from: LocalDate,
    val to: LocalDate,
    val organizationId: Long,
    val granularity: Granularity = Granularity.MONTHLY,
    val accountId: Long? = null,
)

data class CashflowResponse(
    val granularity: Granularity,
    val buckets: List<CashflowBucket>,
)

data class CashflowBucket(
    val key: String,
    val incomeGross: BigDecimal,
    val incomeNet: BigDecimal,
    val expensesGross: BigDecimal,
    val expensesNet: BigDecimal,
    val net: BigDecimal,
)
