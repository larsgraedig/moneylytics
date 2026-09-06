package com.moneylytics.api.adapter.output.persistence

import org.testcontainers.containers.PostgreSQLContainer

object PostgresTestContainer {
    val instance: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:17-alpine").apply { start() }
    }
}
