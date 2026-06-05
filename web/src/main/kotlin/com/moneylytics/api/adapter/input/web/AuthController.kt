package com.moneylytics.api.adapter.input.web

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController {
    @GetMapping("/me")
    suspend fun me(
        @AuthenticationPrincipal oidcUser: OidcUser,
    ): AuthUserResponse =
        AuthUserResponse(
            sub = oidcUser.subject,
            email = oidcUser.email,
            name = oidcUser.fullName,
            picture = oidcUser.picture,
        )
}

data class AuthUserResponse(
    val sub: String,
    val email: String?,
    val name: String?,
    val picture: String?,
)
