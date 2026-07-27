package com.moneylytics.api.application.port.input

fun interface EnrichTransactionUseCase {
    fun enrichByFingerprint(
        fingerprint: String,
        organizationId: Long,
        purpose: String?,
        counterpartyName: String?,
        counterpartyIban: String?,
    )
}
