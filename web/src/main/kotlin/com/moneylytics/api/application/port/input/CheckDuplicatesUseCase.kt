package com.moneylytics.api.application.port.input

fun interface CheckDuplicatesUseCase {
    fun findExistingFingerprints(
        fingerprints: Collection<String>,
        userId: Long,
    ): Set<String>
}
