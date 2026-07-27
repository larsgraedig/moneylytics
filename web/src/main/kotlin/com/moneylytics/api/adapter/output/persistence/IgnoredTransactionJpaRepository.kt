package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface IgnoredTransactionJpaRepository : JpaRepository<IgnoredTransactionEntity, Long> {
    @Query(
        "SELECT e.fingerprint FROM IgnoredTransactionEntity e WHERE e.fingerprint IN :fingerprints AND e.organization.id = :organizationId",
    )
    fun findExistingFingerprints(
        @Param("fingerprints") fingerprints: Collection<String>,
        @Param("organizationId") organizationId: Long,
    ): List<String>

    fun deleteByFingerprintInAndOrganizationId(
        fingerprints: Collection<String>,
        organizationId: Long,
    )
}
