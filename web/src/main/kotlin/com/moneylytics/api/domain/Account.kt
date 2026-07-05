package com.moneylytics.api.domain

import java.time.LocalDate

data class Account(
    val iban: String,
    val name: String,
    val latestTransactionDate: LocalDate? = null,
)
