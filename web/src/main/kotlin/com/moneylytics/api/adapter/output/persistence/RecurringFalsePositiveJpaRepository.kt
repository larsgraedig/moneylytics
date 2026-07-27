package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RecurringFalsePositiveJpaRepository : JpaRepository<RecurringFalsePositiveEntity, Long> {
    fun findByOrganizationId(organizationId: Long): List<RecurringFalsePositiveEntity>

    @Modifying
    @Query(
        "DELETE FROM RecurringFalsePositiveEntity f WHERE f.organization.id = :organizationId AND f.fingerprint IN :fingerprints",
    )
    fun deleteByOrganizationIdAndFingerprintIn(
        @Param("organizationId") organizationId: Long,
        @Param("fingerprints") fingerprints: List<String>,
    )
}
