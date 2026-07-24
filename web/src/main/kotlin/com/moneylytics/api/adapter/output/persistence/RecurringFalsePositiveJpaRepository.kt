package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RecurringFalsePositiveJpaRepository : JpaRepository<RecurringFalsePositiveEntity, Long> {
    fun findByUserId(userId: Long): List<RecurringFalsePositiveEntity>

    @Modifying
    @Query("DELETE FROM RecurringFalsePositiveEntity f WHERE f.user.id = :userId AND f.fingerprint IN :fingerprints")
    fun deleteByUserIdAndFingerprintIn(
        userId: Long,
        fingerprints: List<String>,
    )
}
