package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import java.time.LocalDate

interface RecurringSeriesRepository {
    fun replaceAllForUser(
        series: List<RecurringSeries>,
        userId: Long,
    )

    fun findByUserId(userId: Long): List<RecurringSeries>

    fun updateType(
        seriesId: Long,
        type: RecurringType,
    )

    fun save(
        series: RecurringSeries,
        userId: Long,
    ): RecurringSeries

    fun deleteByIdAndUserId(
        seriesId: Long,
        userId: Long,
    )

    fun findAllUserIds(): Set<Long>

    fun findMemberTransactionIds(seriesId: Long): Set<Long>

    fun addMembers(
        seriesId: Long,
        transactionIds: List<Long>,
    )

    fun updateSeriesMetadata(
        seriesId: Long,
        lastSeen: LocalDate,
        nextExpectedDate: LocalDate,
        occurrenceCount: Int,
    )
}
