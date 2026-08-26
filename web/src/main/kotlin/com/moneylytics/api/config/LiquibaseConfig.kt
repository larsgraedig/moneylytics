package com.moneylytics.api.config

import liquibase.integration.spring.SpringLiquibase
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

@Configuration
@Profile("!local")
class LiquibaseConfig(
    private val dataSource: DataSource,
    @Value("\${spring.liquibase.change-log}") private val changeLog: String,
) {
    @Bean
    fun liquibase(): SpringLiquibase =
        SpringLiquibase().also {
            it.dataSource = dataSource
            it.changeLog = changeLog
        }
}
