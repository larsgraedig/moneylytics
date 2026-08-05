package com.moneylytics.api.domain

import java.math.BigDecimal

data class CategoryClassifierFeatures(
    val purpose: String?,
    val counterpartyName: String?,
    val counterpartyIban: String?,
    val amount: BigDecimal? = null,
)
