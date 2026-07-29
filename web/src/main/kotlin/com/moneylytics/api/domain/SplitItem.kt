package com.moneylytics.api.domain

import java.math.BigDecimal

data class SplitItem(
    val amount: BigDecimal,
    val category: String?,
    val subcategory: String?,
    val categoryGroup: String?,
    val comment: String?,
)
