package com.moneylytics.api.domain

import java.math.BigDecimal
import java.time.LocalDate

data class RecurringExpectedSlot(
    val expectedDate: LocalDate,
    val transactionId: Long,
    val date: LocalDate,
    val amount: BigDecimal,
    val counterpartyName: String? = null,
    val purpose: String? = null,
)
