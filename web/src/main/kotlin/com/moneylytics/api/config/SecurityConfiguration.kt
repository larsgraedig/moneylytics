package com.moneylytics.api.config

import com.moneylytics.api.application.port.input.ResolveUserUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.logout.HttpStatusReturningServerLogoutSuccessHandler
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Configuration
@EnableWebFluxSecurity
class SecurityConfiguration(
    @Value("\${app.frontend-url}") private val frontendUrl: String,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .authorizeExchange { auth ->
                auth
                    .pathMatchers("/oauth2/**", "/login/**")
                    .permitAll()
                    .pathMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    .anyExchange()
                    .authenticated()
            }.oauth2Login { oauth2: OAuth2LoginSpec ->
                oauth2.authenticationSuccessHandler { exchange, authentication ->
                    val oidcUser = authentication.principal as OidcUser
                    Mono
                        .fromCallable { resolveUserUseCase.resolveUser(oidcUser.subject) }
                        .subscribeOn(Schedulers.boundedElastic())
                        .then(
                            RedirectServerAuthenticationSuccessHandler(frontendUrl)
                                .onAuthenticationSuccess(exchange, authentication),
                        )
                }
            }.logout { logout ->
                logout.logoutUrl("/auth/logout")
                logout.logoutSuccessHandler(HttpStatusReturningServerLogoutSuccessHandler(HttpStatus.OK))
            }.exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
            }.build()
}
