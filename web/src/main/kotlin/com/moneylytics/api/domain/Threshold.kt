package com.moneylytics.api.domain

import java.math.BigDecimal

enum class ThresholdPeriod { WEEKLY, MONTHLY, QUARTERLY, YEARLY }

data class Threshold(
    val id: Long,
    val category: String,
    val subcategory: String?,
    val group: String? = null,
    val period: ThresholdPeriod,
    val notice: BigDecimal?,
    val warning: BigDecimal?,
    val critical: BigDecimal?,
)
