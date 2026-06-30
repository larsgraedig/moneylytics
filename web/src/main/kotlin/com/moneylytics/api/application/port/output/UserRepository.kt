package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.User
import com.moneylytics.api.domain.UserSettings

interface UserRepository {
    fun findByExternalId(externalId: String): User?

    fun save(
        externalId: String,
        passwordHash: String?,
    ): User

    fun findAll(): List<User>

    fun getSettings(userId: Long): UserSettings

    fun updateSettings(
        userId: Long,
        defaultAccountIban: String?,
        language: String?,
    ): UserSettings
}
