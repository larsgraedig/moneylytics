package com.moneylytics.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.server.session.CookieWebSessionIdResolver
import java.time.Duration

@Profile("demo")
@Configuration
class DemoSessionConfig(
    @Value("\${server.port}") private val serverPort: Int,
) {
    @Bean
    fun webSessionIdResolver(): CookieWebSessionIdResolver =
        CookieWebSessionIdResolver().apply {
            setCookieName("SESSION_$serverPort")
            setCookieMaxAge(Duration.ofDays(SESSION_DURATION_DAYS))
        }

    companion object {
        private const val SESSION_DURATION_DAYS = 7L
    }
}
