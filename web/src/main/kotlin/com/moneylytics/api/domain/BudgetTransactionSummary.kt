package com.moneylytics.api.domain

import java.math.BigDecimal

data class BudgetTransactionSummary(
    val linkId: Long,
    val budgetId: Long,
    val budgetName: String,
    val amount: BigDecimal?,
)
