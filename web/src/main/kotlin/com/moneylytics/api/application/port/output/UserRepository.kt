package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.User

interface UserRepository {
    fun findByExternalId(externalId: String): User?

    fun save(externalId: String): User
}
