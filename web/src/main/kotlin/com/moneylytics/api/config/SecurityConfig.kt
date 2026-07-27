package com.moneylytics.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.context.ServerSecurityContextRepository

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val userDetailsService: ReactiveUserDetailsService,
    private val oAuth2SuccessHandler: ServerAuthenticationSuccessHandler,
    private val passwordEncoder: PasswordEncoder,
) {
    @Bean
    fun authManager(): ReactiveAuthenticationManager =
        UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService).also {
            it.setPasswordEncoder(passwordEncoder)
        }

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        securityContextRepository: ServerSecurityContextRepository,
    ): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .securityContextRepository(securityContextRepository)
            .authorizeExchange { auth ->
                auth
                    .pathMatchers("/auth/login", "/auth/register", "/oauth2/authorization/**", "/login/oauth2/code/**")
                    .permitAll()
                    .pathMatchers("/admin/**")
                    .hasRole("SYSTEM_ADMIN")
                    .anyExchange()
                    .authenticated()
            }.httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .oauth2Login { oauth2 ->
                oauth2.authenticationSuccessHandler(oAuth2SuccessHandler)
            }.build()
}
