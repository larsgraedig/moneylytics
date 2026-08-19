package com.moneylytics.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "stripe")
data class StripeProperties(
    val secretKey: String = "",
    val webhookSecret: String = "",
    val priceIdMonthly: String = "",
    val priceIdYearly: String = "",
    val publishableKey: String = "",
)

@Configuration
@EnableConfigurationProperties(StripeProperties::class)
class StripeConfig
