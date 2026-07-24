package com.moneylytics.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MoneylyticsApiApplication

fun main(args: Array<String>) {
    runApplication<MoneylyticsApiApplication>(*args)
}
