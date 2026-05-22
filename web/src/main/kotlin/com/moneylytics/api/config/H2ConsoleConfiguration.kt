package com.moneylytics.api.config

import org.h2.tools.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class H2ConsoleConfiguration {
    @Bean(initMethod = "start", destroyMethod = "stop")
    fun h2WebServer(): Server = Server.createWebServer("-webPort", "8082", "-webAllowOthers")
}
