package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface IgnoredTransactionJpaRepository : JpaRepository<IgnoredTransactionEntity, Long> {
    @Query("SELECT e.fingerprint FROM IgnoredTransactionEntity e WHERE e.fingerprint IN :fingerprints AND e.user.id = :userId")
    fun findExistingFingerprints(
        fingerprints: Collection<String>,
        userId: Long,
    ): List<String>

    fun deleteByFingerprintInAndUserId(
        fingerprints: Collection<String>,
        userId: Long,
    )
}
