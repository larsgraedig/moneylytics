package com.moneylytics.api.domain

import java.math.BigDecimal
import java.time.LocalDate

data class AccountBalance(
    val amount: BigDecimal,
    val date: LocalDate,
)
