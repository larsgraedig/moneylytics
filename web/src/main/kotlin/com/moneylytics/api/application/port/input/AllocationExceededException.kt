package com.moneylytics.api.application.port.input

import java.math.BigDecimal

class AllocationExceededException(
    val transactionId: Long,
    val maxRemaining: BigDecimal,
    val existingLinks: List<ExistingLinkSummary>,
) : RuntimeException("Allocation exceeded for transaction $transactionId")

data class ExistingLinkSummary(
    val linkId: Long,
    val linkedTransactionId: Long,
    val committedAmount: BigDecimal,
)
