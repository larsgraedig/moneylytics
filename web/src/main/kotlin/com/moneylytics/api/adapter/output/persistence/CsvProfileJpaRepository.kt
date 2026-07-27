package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CsvProfileJpaRepository : JpaRepository<CsvProfileEntity, Long> {
    fun findByOrganizationIdAndFingerprint(
        organizationId: Long,
        fingerprint: String,
    ): CsvProfileEntity?
}
