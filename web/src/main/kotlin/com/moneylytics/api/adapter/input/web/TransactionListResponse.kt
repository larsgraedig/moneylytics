package com.moneylytics.api.adapter.input.web

import java.math.BigDecimal

data class TransactionListResponse(
    val transactions: List<TransactionItem>,
    val total: BigDecimal,
)

data class TransactionItem(
    val id: Long,
    val bookingDate: String,
    val accountIban: String,
    val category: String,
    val subcategory: String,
    val amount: BigDecimal,
    val currency: String,
)
