package com.moneylytics.api.config

import com.moneylytics.api.application.port.input.LoginOAuthUserUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.server.DefaultServerRedirectStrategy
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.context.ServerSecurityContextRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.URI

@Component
class OAuth2AuthenticationSuccessHandler(
    private val loginOAuthUserUseCase: LoginOAuthUserUseCase,
    private val userDetailsService: ReactiveUserDetailsService,
    private val securityContextRepository: ServerSecurityContextRepository,
    @Value("\${app.oauth2-success-redirect:/}") private val successRedirectUrl: String,
) : ServerAuthenticationSuccessHandler {
    private val redirectStrategy = DefaultServerRedirectStrategy()

    override fun onAuthenticationSuccess(
        webFilterExchange: WebFilterExchange,
        authentication: Authentication,
    ): Mono<Void> {
        val email =
            when (val p = authentication.principal) {
                is OidcUser -> p.email
                is OAuth2User -> p.getAttribute<String>("email")
                else -> null
            } ?: return Mono.error(IllegalStateException("No email claim in OAuth2 token"))

        return Mono
            .fromCallable { loginOAuthUserUseCase.loginOAuthUser(email) }
            .subscribeOn(Schedulers.boundedElastic())
            .then(userDetailsService.findByUsername(email))
            .flatMap { userDetails ->
                val newAuth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                securityContextRepository
                    .save(webFilterExchange.exchange, SecurityContextImpl(newAuth))
                    .then(
                        redirectStrategy.sendRedirect(webFilterExchange.exchange, URI.create(successRedirectUrl)),
                    )
            }
    }
}
