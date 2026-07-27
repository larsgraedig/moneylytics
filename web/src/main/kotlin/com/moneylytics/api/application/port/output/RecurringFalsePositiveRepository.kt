package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.RecurringFalsePositive

interface RecurringFalsePositiveRepository {
    fun findFingerprintsByOrganizationId(organizationId: Long): Set<String>

    fun saveAll(entries: List<RecurringFalsePositive>)

    fun deleteByOrganizationIdAndFingerprints(
        organizationId: Long,
        fingerprints: List<String>,
    )
}
