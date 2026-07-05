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
        val totalOffset = offsetLinks.sumOf { minOf(it.myCommitted.abs(), it.linkedTransactionAmount.abs()) }
        return if (amount >= BigDecimal.ZERO) amount - totalOffset else amount + totalOffset
    }
}
