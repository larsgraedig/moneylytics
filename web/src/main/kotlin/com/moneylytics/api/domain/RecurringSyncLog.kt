package com.moneylytics.api.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class RecurringSyncTrigger { SCHEDULED, MANUAL }

data class RecurringSyncLogEntry(
    val seriesId: Long?,
    val seriesLabel: String,
    val transactionId: Long,
    val bookingDate: LocalDate,
    val amount: BigDecimal,
    val counterpartyName: String?,
)

data class RecurringSyncLog(
    val id: Long? = null,
    val ranAt: Instant,
    val triggeredBy: RecurringSyncTrigger,
    val seriesUpdatedCount: Int,
    val transactionsLinkedCount: Int,
    val entries: List<RecurringSyncLogEntry> = emptyList(),
)
