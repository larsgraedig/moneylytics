package com.moneylytics.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MoneylyticsApiApplication

fun main(args: Array<String>) {
    runApplication<MoneylyticsApiApplication>(*args)
}
