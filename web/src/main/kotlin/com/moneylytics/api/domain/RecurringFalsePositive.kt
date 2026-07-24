package com.moneylytics.api.domain

import java.time.LocalDate

data class RecurringFalsePositive(
    val id: Long? = null,
    val userId: Long,
    val fingerprint: String,
    val createdAt: LocalDate,
)
