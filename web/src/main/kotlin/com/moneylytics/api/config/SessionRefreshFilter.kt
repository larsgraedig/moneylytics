package com.moneylytics.api.config

import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import org.springframework.web.server.session.CookieWebSessionIdResolver
import reactor.core.publisher.Mono

@Component
class SessionRefreshFilter(
    private val sessionIdResolver: CookieWebSessionIdResolver,
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        exchange.response.beforeCommit {
            exchange.session
                .filter { it.isStarted }
                .doOnNext { session -> sessionIdResolver.setSessionId(exchange, session.id) }
                .then()
        }
        return chain.filter(exchange)
    }
}
