package com.moneylytics.api.domain

import java.math.BigDecimal

data class SplitItem(
    val amount: BigDecimal,
    val categoryId: Long?,
    val comment: String?,
)
