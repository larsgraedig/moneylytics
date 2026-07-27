package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface RecurringClassifierTokenCountJpaRepository : JpaRepository<RecurringClassifierTokenCountEntity, Long> {
    fun findByOrganizationId(organizationId: Long): List<RecurringClassifierTokenCountEntity>

    fun findByOrganizationIdAndTypeAndToken(
        organizationId: Long,
        type: String,
        token: String,
    ): RecurringClassifierTokenCountEntity?

    fun existsByOrganizationId(organizationId: Long): Boolean
}

interface RecurringClassifierClassCountJpaRepository : JpaRepository<RecurringClassifierClassCountEntity, Long> {
    fun findByOrganizationId(organizationId: Long): List<RecurringClassifierClassCountEntity>

    fun findByOrganizationIdAndType(
        organizationId: Long,
        type: String,
    ): RecurringClassifierClassCountEntity?
}
