package com.moneylytics.api.domain

import java.math.BigDecimal
import java.time.LocalDate

data class Transaction(
    val category: String?,
    val subcategory: String?,
    val categoryGroup: String? = null,
    val bookingDate: LocalDate,
    val valueDate: LocalDate,
    val accountingDate: LocalDate,
    val amount: BigDecimal,
    val currency: String,
    val accountIban: String,
    val id: Long? = null,
    val offsetLinks: List<TransactionOffsetLink> = emptyList(),
    val groups: List<TransactionGroupSummary> = emptyList(),
    val comment: String? = null,
    val purpose: String? = null,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
) {
    fun effectiveAmount(): BigDecimal {
        if (offsetLinks.isEmpty()) return amount
        // If any link uses this side's full amount (null side → myCommitted == amount),
        // return the original amount to avoid counting it multiple times.
        if (offsetLinks.any { it.myCommitted.compareTo(amount) == 0 }) return amount
        return offsetLinks.sumOf { it.myCommitted }
    }
}
