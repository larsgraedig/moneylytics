package com.moneylytics.api.config

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class SpaWebFilter : WebFilter {
    companion object {
        private val API_PREFIXES =
            setOf(
                "/transactions",
                "/accounts",
                "/categories",
                "/users",
                "/thresholds",
                "/budgets",
                "/collections",
                "/auth",
                "/oauth2",
                "/login",
                "/admin",
                "/organizations",
                "/invitations",
                "/swagger-ui",
                "/v3/api-docs",
            )
        private val INDEX_HTML = ClassPathResource("static/index.html")
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val path = exchange.request.path.value()
        val isApiPath = API_PREFIXES.any { path.startsWith(it) }
        val hasFileExtension = path.substringAfterLast('/').contains('.')

        if (isApiPath || hasFileExtension || !INDEX_HTML.exists()) {
            return chain.filter(exchange)
        }

        return exchange.response.let { response ->
            response.headers.contentType = MediaType.TEXT_HTML
            response.writeWith(
                Mono.fromCallable {
                    response.bufferFactory().wrap(INDEX_HTML.contentAsByteArray)
                },
            )
        }
    }
}
