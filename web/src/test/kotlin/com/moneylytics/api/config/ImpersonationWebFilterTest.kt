package com.moneylytics.api.config

import com.moneylytics.api.config.ImpersonationWebFilter.Companion.IMPERSONATED_USER_ID_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class ImpersonationWebFilterTest {
    private val userDetailsService: ReactiveUserDetailsService = mock()
    private val filter = ImpersonationWebFilter(userDetailsService)

    private fun buildExchange(path: String = "/transactions/recurring"): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.get(path).build())

    private fun buildChain(capturedUsername: MutableList<String>): WebFilterChain =
        WebFilterChain { exchange ->
            ReactiveSecurityContextHolder
                .getContext()
                .doOnNext { ctx -> capturedUsername.add(ctx.authentication?.name ?: "none") }
                .thenEmpty(Mono.empty())
        }

    private fun systemAdminContext(username: String): SecurityContext {
        val auth =
            UsernamePasswordAuthenticationToken(
                username,
                null,
                listOf(SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")),
            )
        return SecurityContextImpl(auth)
    }

    @Test
    fun `should pass through without impersonation when no session attribute is set`() {
        val exchange = buildExchange()
        val captured = mutableListOf<String>()
        val chain = buildChain(captured)

        StepVerifier
            .create(
                filter
                    .filter(exchange, chain)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(systemAdminContext("admin@test.de")))),
            ).verifyComplete()

        assertThat(captured).containsExactly("admin@test.de")
    }

    @Test
    fun `should replace security context with impersonated user when system admin has active impersonation`() {
        val exchange = buildExchange()
        exchange.session.block()!!.attributes[IMPERSONATED_USER_ID_KEY] = "target@test.de"

        val targetDetails: UserDetails =
            User
                .withUsername("target@test.de")
                .password("x")
                .roles("USER")
                .build()
        whenever(userDetailsService.findByUsername("target@test.de")).thenReturn(Mono.just(targetDetails))

        val captured = mutableListOf<String>()
        val chain = buildChain(captured)

        StepVerifier
            .create(
                filter
                    .filter(exchange, chain)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(systemAdminContext("admin@test.de")))),
            ).verifyComplete()

        assertThat(captured).containsExactly("target@test.de")
    }

    @Test
    fun `should not impersonate when caller is not a system admin`() {
        val exchange = buildExchange()
        exchange.session.block()!!.attributes[IMPERSONATED_USER_ID_KEY] = "target@test.de"

        val captured = mutableListOf<String>()
        val chain = buildChain(captured)

        val regularUserAuth =
            UsernamePasswordAuthenticationToken(
                "regular@test.de",
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
        val regularCtx = SecurityContextImpl(regularUserAuth)

        StepVerifier
            .create(
                filter
                    .filter(exchange, chain)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(regularCtx))),
            ).verifyComplete()

        assertThat(captured).containsExactly("regular@test.de")
    }

    @Test
    fun `should skip impersonation for admin paths`() {
        val exchange = buildExchange(path = "/admin/something")
        exchange.session.block()!!.attributes[IMPERSONATED_USER_ID_KEY] = "target@test.de"

        val captured = mutableListOf<String>()
        val chain = buildChain(captured)

        StepVerifier
            .create(
                filter
                    .filter(exchange, chain)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(systemAdminContext("admin@test.de")))),
            ).verifyComplete()

        assertThat(captured).containsExactly("admin@test.de")
    }
}
