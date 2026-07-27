package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import java.time.LocalDate

interface RecurringSeriesRepository {
    fun replaceAllForOrganization(
        series: List<RecurringSeries>,
        organizationId: Long,
    )

    fun findByOrganizationId(organizationId: Long): List<RecurringSeries>

    fun updateType(
        seriesId: Long,
        type: RecurringType,
    )

    fun save(
        series: RecurringSeries,
        organizationId: Long,
    ): RecurringSeries

    fun deleteByIdAndOrganizationId(
        seriesId: Long,
        organizationId: Long,
    )

    fun findAllOrganizationIds(): Set<Long>

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
