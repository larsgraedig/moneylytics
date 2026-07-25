package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.domain.RecurringSyncLog
import com.moneylytics.api.domain.RecurringSyncTrigger
import java.math.BigDecimal

data class RecurringSyncLogEntryItem(
    val seriesId: Long?,
    val seriesLabel: String,
    val transactionId: Long,
    val bookingDate: String,
    val amount: BigDecimal,
    val counterpartyName: String?,
)

data class RecurringSyncLogItem(
    val id: Long?,
    val ranAt: String,
    val triggeredBy: RecurringSyncTrigger,
    val seriesUpdatedCount: Int,
    val transactionsLinkedCount: Int,
    val entries: List<RecurringSyncLogEntryItem>,
)

fun RecurringSyncLog.toItem() =
    RecurringSyncLogItem(
        id = id,
        ranAt = ranAt.toString(),
        triggeredBy = triggeredBy,
        seriesUpdatedCount = seriesUpdatedCount,
        transactionsLinkedCount = transactionsLinkedCount,
        entries =
            entries.map { e ->
                RecurringSyncLogEntryItem(
                    seriesId = e.seriesId,
                    seriesLabel = e.seriesLabel,
                    transactionId = e.transactionId,
                    bookingDate = e.bookingDate.toString(),
                    amount = e.amount,
                    counterpartyName = e.counterpartyName,
                )
            },
    )
