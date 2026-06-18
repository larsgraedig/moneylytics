package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Account

interface SaveAccountUseCase {
    fun saveAccount(
        iban: String,
        name: String,
        userId: Long,
    ): Account
}
