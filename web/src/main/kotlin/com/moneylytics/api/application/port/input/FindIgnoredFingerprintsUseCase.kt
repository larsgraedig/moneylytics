package com.moneylytics.api.application.port.input

fun interface FindIgnoredFingerprintsUseCase {
    fun findIgnoredFingerprints(
        fingerprints: Collection<String>,
        userId: Long,
    ): Set<String>
}
