package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetUserSettingsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.UpdateUserSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/users/settings")
class UserSettingsController(
    private val resolveUserUseCase: ResolveUserUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val updateUserSettingsUseCase: UpdateUserSettingsUseCase,
) {
    @GetMapping
    suspend fun getSettings(
        @AuthenticationPrincipal principal: UserDetails,
    ): UserSettingsResponse =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            getUserSettingsUseCase.getSettings(userId).toResponse()
        }

    @PatchMapping
    suspend fun updateSettings(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: UpdateUserSettingsRequest,
        exchange: ServerWebExchange,
    ): UserSettingsResponse {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            updateUserSettingsUseCase
                .updateSettings(
                    userId = userId,
                    organizationId = organizationId,
                    defaultAccountIban = request.defaultAccountIban,
                    language = request.language,
                    transactionsColumnOrder = request.transactionsColumnOrder,
                ).toResponse()
        }
    }

    private fun com.moneylytics.api.domain.UserSettings.toResponse() =
        UserSettingsResponse(
            defaultAccountIban = defaultAccountIban,
            language = language,
            transactionsColumnOrder = transactionsColumnOrder,
        )
}

data class UserSettingsResponse(
    val defaultAccountIban: String?,
    val language: String?,
    val transactionsColumnOrder: List<String>?,
)

data class UpdateUserSettingsRequest(
    val defaultAccountIban: String?,
    val language: String?,
    val transactionsColumnOrder: List<String>?,
)
