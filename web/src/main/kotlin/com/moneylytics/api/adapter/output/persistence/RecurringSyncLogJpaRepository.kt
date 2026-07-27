package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface RecurringSyncLogJpaRepository : JpaRepository<RecurringSyncLogEntity, Long> {
    fun findByOrganizationIdOrderByRanAtDesc(
        organizationId: Long,
        pageable: Pageable,
    ): List<RecurringSyncLogEntity>
}

interface RecurringSyncLogEntryJpaRepository : JpaRepository<RecurringSyncLogEntryEntity, Long> {
    fun findByLogIdIn(logIds: Collection<Long>): List<RecurringSyncLogEntryEntity>
}
