package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.UserSettings

fun interface GetUserSettingsUseCase {
    fun getSettings(userId: Long): UserSettings
}

fun interface UpdateUserSettingsUseCase {
    fun updateSettings(
        userId: Long,
        defaultAccountIban: String?,
        language: String?,
        transactionsColumnOrder: List<String>?,
    ): UserSettings
}
