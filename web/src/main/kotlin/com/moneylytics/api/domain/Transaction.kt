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
    val comment: String? = null,
    val purpose: String? = null,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
) {
    fun effectiveAmount(): BigDecimal = amount + offsetLinks.sumOf { it.contribution }
}
