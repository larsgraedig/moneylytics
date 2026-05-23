package com.moneylytics.api.domain

import java.math.BigDecimal
import java.time.LocalDate

data class Transaction(
    val category: String,
    val subcategory: String,
    val bookingDate: LocalDate,
    val valueDate: LocalDate,
    val amount: BigDecimal,
    val currency: String,
    val accountIban: String,
)
