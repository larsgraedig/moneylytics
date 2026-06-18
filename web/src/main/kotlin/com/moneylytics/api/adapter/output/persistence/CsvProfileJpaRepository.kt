package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CsvProfileJpaRepository : JpaRepository<CsvProfileEntity, Long> {
    fun findByUserIdAndFingerprint(
        userId: Long,
        fingerprint: String,
    ): CsvProfileEntity?
}
