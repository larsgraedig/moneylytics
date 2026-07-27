package com.moneylytics.api.application.port.input

fun interface FindIgnoredFingerprintsUseCase {
    fun findIgnoredFingerprints(
        fingerprints: Collection<String>,
        organizationId: Long,
    ): Set<String>
}
