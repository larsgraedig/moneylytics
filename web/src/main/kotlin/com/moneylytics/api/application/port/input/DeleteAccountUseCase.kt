package com.moneylytics.api.application.port.input

interface DeleteAccountUseCase {
    fun deleteAccount(
        iban: String,
        userId: Long,
    )
}
