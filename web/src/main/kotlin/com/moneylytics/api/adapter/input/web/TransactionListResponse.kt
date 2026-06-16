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
    val effectiveAmount: BigDecimal,
    val currency: String,
    val offsetLinks: List<OffsetLinkItem>,
    val comment: String?,
    val purpose: String?,
)

data class OffsetLinkItem(
    val id: Long,
    val linkedTransactionId: Long,
    val linkedTransactionAmount: BigDecimal,
    val partialAmount: BigDecimal?,
)
