package com.moneylytics.api.domain

import java.math.BigDecimal
import java.time.LocalDate

data class RecurringExpectedOccurrence(
    val id: Long? = null,
    val seriesId: Long,
    val expectedDate: LocalDate,
    val expectedAmount: BigDecimal,
    val matchedTransactionId: Long? = null,
    val matchedDate: LocalDate? = null,
    val matchedAmount: BigDecimal? = null,
)
