package com.moneylytics.api.application.port.output

interface IgnoredTransactionRepository {
    fun findExistingFingerprints(
        fingerprints: Collection<String>,
        userId: Long,
    ): Set<String>

    fun saveAll(
        fingerprints: Collection<String>,
        userId: Long,
    )

    fun deleteAll(
        fingerprints: Collection<String>,
        userId: Long,
    )
}
