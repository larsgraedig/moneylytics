package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.output.UserRepository
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("local | demo")
@RestController
@RequestMapping("/auth/local-info")
class LocalInfoController(
    private val userRepository: UserRepository,
) {
    @GetMapping
    fun localInfo(): List<LocalUserInfo> = allLocalUsers.filter { userRepository.findByExternalId(it.username) != null }

    companion object {
        private val allLocalUsers =
            listOf(
                LocalUserInfo(username = "dev@local.dev", password = "local", tier = "Pro", role = "User", hasOrg = true),
                LocalUserInfo(username = "dev-no-org@local.dev", password = "local", tier = "Pro", role = "User", hasOrg = false),
                LocalUserInfo(username = "standard@local.dev", password = "local", tier = "Standard", role = "User", hasOrg = true),
                LocalUserInfo(username = "standard-no-org@local.dev", password = "local", tier = "Standard", role = "User", hasOrg = false),
                LocalUserInfo(username = "pastdue@local.dev", password = "local", tier = "Pro", role = "User", hasOrg = true),
                LocalUserInfo(username = "pastdue-no-org@local.dev", password = "local", tier = "Pro", role = "User", hasOrg = false),
                LocalUserInfo(username = "canceled@local.dev", password = "local", tier = "Standard", role = "User", hasOrg = true),
                LocalUserInfo(username = "canceled-no-org@local.dev", password = "local", tier = "Standard", role = "User", hasOrg = false),
                LocalUserInfo(username = "admin@local.dev", password = "admin", tier = "Standard", role = "Admin", hasOrg = true),
                LocalUserInfo(username = "admin-no-org@local.dev", password = "admin", tier = "Standard", role = "Admin", hasOrg = false),
            )
    }
}

data class LocalUserInfo(
    val username: String,
    val password: String,
    val tier: String,
    val role: String,
    val hasOrg: Boolean,
)
