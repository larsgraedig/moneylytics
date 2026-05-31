package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.User

interface ListUsersUseCase {
    fun listUsers(): List<User>
}
