package com.moneylytics.api.domain

import java.math.BigDecimal
import java.time.LocalDate

data class BudgetTransactionLink(
    val id: Long,
    val budgetId: Long,
    val transactionId: Long,
    val amount: BigDecimal?,
    val transactionAmount: BigDecimal,
    val transactionDate: LocalDate,
    val transactionCategory: String,
    val transactionSubcategory: String,
    val transactionPurpose: String?,
    val transactionComment: String?,
) {
    fun effectiveAmount(): BigDecimal =
        amount?.let { if (transactionAmount.signum() < 0) it.abs().negate() else it.abs() }
            ?: transactionAmount
}
