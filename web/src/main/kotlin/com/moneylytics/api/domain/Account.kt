package com.moneylytics.api.domain

import java.math.BigDecimal
import java.time.LocalDate

data class Account(
    val id: Long? = null,
    val iban: String,
    val name: String,
    val latestTransactionDate: LocalDate? = null,
    val balance: BigDecimal? = null,
    val balanceDate: LocalDate? = null,
)
