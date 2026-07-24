package com.moneylytics.api.domain

import java.math.BigDecimal

data class ClassifierFeatures(
    val label: String,
    val cadence: RecurrenceCadence,
    val direction: RecurrenceDirection,
    val expectedAmount: BigDecimal,
)

fun RecurringSeries.toFeatures() =
    ClassifierFeatures(
        label = label,
        cadence = cadence,
        direction = direction,
        expectedAmount = expectedAmount,
    )
