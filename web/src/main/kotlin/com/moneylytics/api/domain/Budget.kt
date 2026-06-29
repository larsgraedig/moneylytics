package com.moneylytics.api.domain

import java.math.BigDecimal

data class Budget(
    val id: Long? = null,
    val name: String,
    val targetAmount: BigDecimal? = null,
    val note: String? = null,
)
