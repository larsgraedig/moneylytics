package com.moneylytics.api.application.port.input

fun interface CheckDuplicatesUseCase {
    fun findExistingFingerprints(
        fingerprints: Collection<String>,
        organizationId: Long,
    ): Set<String>
}
