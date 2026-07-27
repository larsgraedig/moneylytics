package com.moneylytics.api.config

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class ImpersonationWebFilter(
    private val userDetailsService: ReactiveUserDetailsService,
) : WebFilter {
    companion object {
        const val IMPERSONATED_USER_ID_KEY = "IMPERSONATED_USER_ID"
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        if (exchange.request.path
                .value()
                .startsWith("/admin/")
        ) {
            return chain.filter(exchange)
        }
        return exchange.session.flatMap { session ->
            val impersonatedId = session.getAttribute<String>(IMPERSONATED_USER_ID_KEY)
            if (impersonatedId == null) {
                return@flatMap chain.filter(exchange)
            }
            ReactiveSecurityContextHolder.getContext().flatMap { ctx ->
                val isSystemAdmin =
                    ctx.authentication
                        ?.authorities
                        ?.any { it.authority == "ROLE_SYSTEM_ADMIN" } == true
                if (!isSystemAdmin) {
                    return@flatMap chain.filter(exchange)
                }
                userDetailsService.findByUsername(impersonatedId).flatMap { details ->
                    val impAuth = UsernamePasswordAuthenticationToken(details, null, details.authorities)
                    chain
                        .filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(impAuth))
                }
            }
        }
    }
}
