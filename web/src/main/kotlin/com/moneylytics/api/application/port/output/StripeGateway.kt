package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.BillingInterval
import com.moneylytics.api.domain.SubscriptionSetupResult

interface StripeGateway {
    fun createCustomer(email: String): String

    fun createSubscription(
        customerId: String,
        interval: BillingInterval,
    ): SubscriptionSetupResult

    fun cancelSubscription(subscriptionId: String)

    fun downloadInvoicePdf(pdfUrl: String): ByteArray

    fun downloadInvoicePdfById(stripeInvoiceId: String): ByteArray

    fun retrieveInvoiceNumber(stripeInvoiceId: String): String?
}
