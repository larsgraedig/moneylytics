package com.moneylytics.api.config

import com.moneylytics.api.application.port.input.ResolveUserUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.logout.HttpStatusReturningServerLogoutSuccessHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Configuration
@EnableWebFluxSecurity
class SecurityConfiguration(
    @Value("\${app.frontend-url}") private val frontendUrl: String,
    private val resolveUserUseCase: ResolveUserUseCase,
    private val clientRegistrationRepository: ReactiveClientRegistrationRepository,
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
                oauth2
                    .authorizationRequestResolver(appleAwareRequestResolver())
                    .authenticationSuccessHandler { exchange, authentication ->
                        val principal = authentication.principal as OAuth2User
                        val externalId = if (principal is OidcUser) principal.subject else principal.name
                        Mono
                            .fromCallable { resolveUserUseCase.resolveUser(externalId) }
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

    // Apple requires response_mode=form_post (it POSTs the authorization code back).
    // Spring Security's WebFlux OAuth2 filter reads both query params and form body,
    // so no further changes are needed beyond injecting this parameter.
    private fun appleAwareRequestResolver(): ServerOAuth2AuthorizationRequestResolver {
        val delegate = DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository)
        return object : ServerOAuth2AuthorizationRequestResolver {
            override fun resolve(exchange: ServerWebExchange): Mono<OAuth2AuthorizationRequest> {
                val registrationId =
                    exchange.request.path
                        .pathWithinApplication()
                        .value()
                        .substringAfterLast('/')
                return delegate.resolve(exchange).map { addResponseModeIfApple(it, registrationId) }
            }

            override fun resolve(
                exchange: ServerWebExchange,
                clientRegistrationId: String,
            ): Mono<OAuth2AuthorizationRequest> =
                delegate
                    .resolve(exchange, clientRegistrationId)
                    .map { addResponseModeIfApple(it, clientRegistrationId) }
        }
    }

    private fun addResponseModeIfApple(
        request: OAuth2AuthorizationRequest,
        registrationId: String,
    ): OAuth2AuthorizationRequest =
        if (registrationId == "apple") {
            OAuth2AuthorizationRequest
                .from(request)
                .additionalParameters { it["response_mode"] = "form_post" }
                .build()
        } else {
            request
        }
}
