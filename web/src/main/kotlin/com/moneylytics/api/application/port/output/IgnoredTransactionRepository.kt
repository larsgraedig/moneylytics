package com.moneylytics.api.application.port.output

interface IgnoredTransactionRepository {
    fun findExistingFingerprints(
        fingerprints: Collection<String>,
        organizationId: Long,
    ): Set<String>

    fun saveAll(
        fingerprints: Collection<String>,
        organizationId: Long,
    )

    fun deleteAll(
        fingerprints: Collection<String>,
        organizationId: Long,
    )
}
