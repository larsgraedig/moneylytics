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
    val collections: List<CollectionSummary> = emptyList(),
    val comment: String? = null,
    val purpose: String? = null,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
) {
    fun effectiveAmount(): BigDecimal {
        if (offsetLinks.isEmpty()) return amount
        val totalOffset =
            offsetLinks.sumOf { link ->
                val a = link.amountA
                val b = link.amountB
                when {
                    a == null && b == null -> BigDecimal.ZERO
                    a != null && b != null -> minOf(a.abs(), b.abs())
                    else -> minOf(link.myCommitted.abs(), link.linkedTransactionAmount.abs())
                }
            }
        return if (amount >= BigDecimal.ZERO) amount - totalOffset else amount + totalOffset
    }
}
