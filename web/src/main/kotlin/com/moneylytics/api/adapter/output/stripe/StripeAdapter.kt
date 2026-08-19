package com.moneylytics.api.adapter.output.stripe

import com.moneylytics.api.application.port.output.StripeGateway
import com.moneylytics.api.config.StripeProperties
import com.moneylytics.api.domain.BillingInterval
import com.moneylytics.api.domain.SubscriptionSetupResult
import com.stripe.StripeClient
import com.stripe.param.CustomerCreateParams
import com.stripe.param.SubscriptionCreateParams
import com.stripe.param.SubscriptionUpdateParams
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val logger = KotlinLogging.logger {}

@Component
class StripeAdapter(
    private val stripeProperties: StripeProperties,
) : StripeGateway {
    private val client by lazy { StripeClient(stripeProperties.secretKey) }
    private val httpClient by lazy { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build() }

    override fun createCustomer(email: String): String {
        val params = CustomerCreateParams.builder().setEmail(email).build()
        return client.customers().create(params).id
    }

    override fun createSubscription(
        customerId: String,
        interval: BillingInterval,
    ): SubscriptionSetupResult {
        val priceId =
            when (interval) {
                BillingInterval.MONTHLY -> stripeProperties.priceIdMonthly
                BillingInterval.YEARLY -> stripeProperties.priceIdYearly
            }

        val params =
            SubscriptionCreateParams
                .builder()
                .setCustomer(customerId)
                .addItem(
                    SubscriptionCreateParams.Item
                        .builder()
                        .setPrice(priceId)
                        .build(),
                ).setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                .addAllExpand(listOf("latest_invoice.payment_intent"))
                .build()

        val subscription = client.subscriptions().create(params)
        val clientSecret =
            subscription.latestInvoiceObject?.paymentIntentObject?.clientSecret
                ?: error("No client_secret returned from Stripe — check subscription payment_behavior")

        return SubscriptionSetupResult(
            subscriptionId = subscription.id,
            clientSecret = clientSecret,
            currentPeriodEnd = subscription.currentPeriodEnd,
            priceId =
                subscription.items
                    ?.data
                    ?.firstOrNull()
                    ?.price
                    ?.id,
        )
    }

    override fun cancelSubscription(subscriptionId: String) {
        val params = SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build()
        client.subscriptions().update(subscriptionId, params)
    }

    override fun downloadInvoicePdf(pdfUrl: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(pdfUrl)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) {
            logger.warn { "Failed to download invoice PDF from $pdfUrl — HTTP ${response.statusCode()}" }
            error("PDF download failed with status ${response.statusCode()}")
        }
        return response.body()
    }

    override fun downloadInvoicePdfById(stripeInvoiceId: String): ByteArray {
        val invoice = client.invoices().retrieve(stripeInvoiceId)
        val pdfUrl = invoice.invoicePdf ?: error("No PDF URL available for invoice $stripeInvoiceId")
        return downloadInvoicePdf(pdfUrl)
    }

    override fun retrieveInvoiceNumber(stripeInvoiceId: String): String? = client.invoices().retrieve(stripeInvoiceId).number
}
