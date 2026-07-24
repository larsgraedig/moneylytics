package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.RecurringFalsePositive

interface RecurringFalsePositiveRepository {
    fun findFingerprintsByUserId(userId: Long): Set<String>

    fun saveAll(entries: List<RecurringFalsePositive>)

    fun deleteByUserIdAndFingerprints(
        userId: Long,
        fingerprints: List<String>,
    )
}
