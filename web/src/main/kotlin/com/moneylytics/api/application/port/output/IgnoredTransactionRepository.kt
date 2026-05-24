package com.moneylytics.api.application.port.output

interface IgnoredTransactionRepository {
    fun findExistingFingerprints(fingerprints: Collection<String>): Set<String>

    fun saveAll(fingerprints: Collection<String>)

    fun deleteAll(fingerprints: Collection<String>)
}
