package com.moneylytics.api.adapter.input.web

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController {
    @GetMapping("/me")
    suspend fun me(
        @AuthenticationPrincipal oauth2User: OAuth2User,
    ): AuthUserResponse =
        if (oauth2User is OidcUser) {
            AuthUserResponse(
                sub = oauth2User.subject,
                email = oauth2User.email,
                name = oauth2User.fullName,
                picture = oauth2User.picture,
            )
        } else {
            // Plain OAuth2 (e.g. Facebook): picture arrives as {"data": {"url": "..."}}
            @Suppress("UNCHECKED_CAST")
            val pictureUrl =
                ((oauth2User.getAttribute<Map<*, *>>("picture"))?.get("data") as? Map<*, *>)
                    ?.get("url") as? String
            AuthUserResponse(
                sub = oauth2User.name,
                email = oauth2User.getAttribute("email"),
                name = oauth2User.getAttribute("name"),
                picture = pictureUrl,
            )
        }
}

data class AuthUserResponse(
    val sub: String,
    val email: String?,
    val name: String?,
    val picture: String?,
)
