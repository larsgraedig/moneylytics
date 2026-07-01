package com.moneylytics.api.application.port.input

fun interface EnrichTransactionUseCase {
    fun enrichByFingerprint(
        fingerprint: String,
        userId: Long,
        purpose: String?,
        counterpartyName: String?,
        counterpartyIban: String?,
    )
}
