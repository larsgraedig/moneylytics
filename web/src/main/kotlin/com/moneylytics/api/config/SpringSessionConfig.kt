package com.moneylytics.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.DataSourceInitializer
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.session.ReactiveSessionRepository
import org.springframework.session.Session
import org.springframework.session.config.annotation.web.server.EnableSpringWebSession
import org.springframework.session.jdbc.JdbcIndexedSessionRepository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.session.CookieWebSessionIdResolver
import java.time.Duration
import javax.sql.DataSource

@Configuration
@EnableSpringWebSession
class SpringSessionConfig {
    companion object {
        private const val SESSION_DURATION_DAYS = 7L
        private val SESSION_DURATION: Duration = Duration.ofDays(SESSION_DURATION_DAYS)
    }

    @Bean
    fun sessionSchemaInitializer(dataSource: DataSource): DataSourceInitializer =
        DataSourceInitializer().apply {
            setDataSource(dataSource)
            setDatabasePopulator(
                ResourceDatabasePopulator(ClassPathResource("schema-spring-session.sql")),
            )
        }

    @Bean
    fun jdbcIndexedSessionRepository(
        dataSource: DataSource,
        transactionManager: PlatformTransactionManager,
    ): JdbcIndexedSessionRepository =
        JdbcIndexedSessionRepository(
            JdbcTemplate(dataSource),
            TransactionTemplate(transactionManager),
        ).apply {
            setDefaultMaxInactiveInterval(SESSION_DURATION)
        }

    @Bean
    fun reactiveSessionRepository(jdbcRepo: JdbcIndexedSessionRepository): ReactiveSessionRepository<Session> =
        ReactiveJdbcSessionRepositoryAdapter(jdbcRepo)

    @Bean
    @Profile("!demo")
    fun webSessionIdResolver(): CookieWebSessionIdResolver =
        CookieWebSessionIdResolver().apply {
            setCookieMaxAge(SESSION_DURATION)
        }
}
