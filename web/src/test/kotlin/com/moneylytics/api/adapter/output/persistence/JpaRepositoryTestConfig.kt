package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.EntityManagerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.Properties
import javax.sql.DataSource

@Configuration
@EnableJpaRepositories(basePackages = ["com.moneylytics.api.adapter.output.persistence"])
@EnableTransactionManagement(proxyTargetClass = true)
class JpaRepositoryTestConfig {
    @Bean
    fun dataSource(): DataSource {
        val container = PostgresTestContainer.instance
        return DriverManagerDataSource().apply {
            setDriverClassName(container.driverClassName)
            url = container.jdbcUrl
            username = container.username
            password = container.password
        }
    }

    @Bean
    fun entityManagerFactory(dataSource: DataSource): LocalContainerEntityManagerFactoryBean {
        val factory = LocalContainerEntityManagerFactoryBean()
        factory.dataSource = dataSource
        factory.jpaVendorAdapter = HibernateJpaVendorAdapter()
        factory.setPackagesToScan("com.moneylytics.api.adapter.output.persistence")
        factory.setJpaProperties(
            Properties().apply {
                setProperty("hibernate.hbm2ddl.auto", "create-drop")
                setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                setProperty("hibernate.show_sql", "false")
            },
        )
        return factory
    }

    @Bean
    fun transactionManager(entityManagerFactory: EntityManagerFactory) = JpaTransactionManager(entityManagerFactory)
}
