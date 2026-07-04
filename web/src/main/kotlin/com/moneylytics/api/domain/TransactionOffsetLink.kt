package com.moneylytics.api.domain

import java.math.BigDecimal

data class TransactionOffsetLink(
    val id: Long,
    val linkedTransactionId: Long,
    val linkedTransactionAmount: BigDecimal,
    val amountA: BigDecimal?,
    val amountB: BigDecimal?,
    val myCommitted: BigDecimal,
) {
    val contribution: BigDecimal get() = -myCommitted
}
