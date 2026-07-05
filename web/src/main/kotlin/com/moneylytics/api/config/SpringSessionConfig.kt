package com.moneylytics.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
import javax.sql.DataSource

@Configuration
@EnableSpringWebSession
class SpringSessionConfig {
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
        )

    @Bean
    fun reactiveSessionRepository(jdbcRepo: JdbcIndexedSessionRepository): ReactiveSessionRepository<Session> =
        ReactiveJdbcSessionRepositoryAdapter(jdbcRepo)
}
