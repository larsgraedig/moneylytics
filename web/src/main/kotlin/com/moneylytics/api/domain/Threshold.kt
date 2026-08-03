package com.moneylytics.api.domain

import java.math.BigDecimal

enum class ThresholdPeriod { WEEKLY, MONTHLY, QUARTERLY, YEARLY }

data class Threshold(
    val id: Long,
    val categoryId: Long,
    val categoryPath: List<String>,
    val period: ThresholdPeriod,
    val notice: BigDecimal?,
    val warning: BigDecimal?,
    val critical: BigDecimal?,
)
