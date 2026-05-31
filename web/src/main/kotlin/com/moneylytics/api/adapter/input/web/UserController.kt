package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.ListUsersUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(
    private val listUsersUseCase: ListUsersUseCase,
) {
    @GetMapping
    suspend fun listUsers(): UsersResponse =
        withContext(Dispatchers.IO) {
            UsersResponse(listUsersUseCase.listUsers().map { it.externalId })
        }
}

data class UsersResponse(
    val users: List<String>,
)
