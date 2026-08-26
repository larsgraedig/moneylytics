package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TransactionImportCsvProfileJpaRepository : JpaRepository<TransactionImportCsvProfileEntity, Long> {
    fun findByOrganizationIdAndFingerprint(
        organizationId: Long,
        fingerprint: String,
    ): TransactionImportCsvProfileEntity?
}
