package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.AssignTierToUserUseCase
import com.moneylytics.api.application.port.input.HandleStripeWebhookUseCase
import com.moneylytics.api.application.port.output.InvoiceRepository
import com.moneylytics.api.application.port.output.StripeCustomerRepository
import com.moneylytics.api.application.port.output.StripeGateway
import com.moneylytics.api.application.port.output.TierRepository
import com.moneylytics.api.config.StripeProperties
import com.moneylytics.api.domain.Invoice
import com.moneylytics.api.domain.SubscriptionStatus
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Subscription
import com.stripe.net.Webhook
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import com.stripe.model.Invoice as StripeInvoice

private val logger = KotlinLogging.logger {}

@Service
class StripeWebhookService(
    private val stripeProperties: StripeProperties,
    private val stripeCustomerRepository: StripeCustomerRepository,
    private val invoiceRepository: InvoiceRepository,
    private val stripeGateway: StripeGateway,
    private val tierRepository: TierRepository,
    private val assignTierToUserUseCase: AssignTierToUserUseCase,
) : HandleStripeWebhookUseCase {
    @Transactional
    override fun handle(
        payload: String,
        signature: String,
    ) {
        val event =
            try {
                Webhook.constructEvent(payload, signature, stripeProperties.webhookSecret)
            } catch (e: SignatureVerificationException) {
                logger.warn { "Invalid Stripe webhook signature: ${e.message}" }
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature")
            } catch (e: Exception) {
                logger.warn { "Malformed Stripe webhook request: ${e.message}" }
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed webhook")
            }

        logger.info { "Received Stripe event: ${event.type}" }

        when (event.type) {
            "invoice.paid" -> handleInvoicePaid(event.dataObjectDeserializer.deserializeUnsafe() as StripeInvoice)
            "invoice.payment_failed" -> handleInvoicePaymentFailed(event.dataObjectDeserializer.deserializeUnsafe() as StripeInvoice)
            "customer.subscription.updated" -> handleSubscriptionUpdated(event.dataObjectDeserializer.deserializeUnsafe() as Subscription)
            "customer.subscription.deleted" -> handleSubscriptionDeleted(event.dataObjectDeserializer.deserializeUnsafe() as Subscription)
            else -> logger.debug { "Unhandled Stripe event type: ${event.type}" }
        }
    }

    private fun handleInvoicePaid(stripeInvoice: StripeInvoice) {
        val customerId = stripeInvoice.customer ?: return
        val stripeCustomer = stripeCustomerRepository.findByStripeCustomerId(customerId) ?: return

        val pdfUrl = stripeInvoice.invoicePdf
        val pdfData =
            runCatching { stripeGateway.downloadInvoicePdfById(stripeInvoice.id) }
                .recoverCatching { pdfUrl?.let { stripeGateway.downloadInvoicePdf(it) } ?: throw it }
                .getOrNull()

        val periodStart = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeInvoice.periodStart), ZoneOffset.UTC)
        val periodEnd = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeInvoice.periodEnd), ZoneOffset.UTC)
        val issuedAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeInvoice.created), ZoneOffset.UTC)

        val invoice =
            Invoice(
                id = 0,
                userId = stripeCustomer.userId,
                stripeInvoiceId = stripeInvoice.id,
                amountCents = stripeInvoice.amountPaid.toInt(),
                currency = stripeInvoice.currency,
                status = "paid",
                periodStart = periodStart,
                periodEnd = periodEnd,
                hasPdf = pdfData != null,
                issuedAt = issuedAt,
            )
        invoiceRepository.save(invoice, pdfData)

        val priceId =
            stripeInvoice.lines
                ?.data
                ?.firstOrNull()
                ?.price
                ?.id
        val tier = priceId?.let { tierRepository.findByStripePriceId(it) }
        if (tier == null) {
            logger.warn { "No tier mapped to price $priceId — skipping tier assignment for invoice ${stripeInvoice.id}" }
            return
        }
        assignTierToUserUseCase.assignTierToUser(stripeCustomer.userId, tier.id)

        logger.info { "Processed paid invoice ${stripeInvoice.id} for user ${stripeCustomer.userId} → tier '${tier.name}'" }
    }

    private fun handleInvoicePaymentFailed(stripeInvoice: StripeInvoice) {
        logger.warn { "Payment failed for invoice ${stripeInvoice.id}, customer ${stripeInvoice.customer}" }
    }

    private fun handleSubscriptionUpdated(subscription: Subscription) {
        val customerId = subscription.customer ?: return
        val status =
            if (subscription.cancelAtPeriodEnd) {
                SubscriptionStatus.CANCELING
            } else {
                mapStripeStatus(subscription.status)
            }
        stripeCustomerRepository.updateSubscription(
            stripeCustomerId = customerId,
            subscriptionId = subscription.id,
            status = status,
            currentPeriodEnd = subscription.currentPeriodEnd,
            priceId =
                subscription.items
                    ?.data
                    ?.firstOrNull()
                    ?.price
                    ?.id,
        )
    }

    private fun handleSubscriptionDeleted(subscription: Subscription) {
        val customerId = subscription.customer ?: return
        val stripeCustomer = stripeCustomerRepository.findByStripeCustomerId(customerId) ?: return

        stripeCustomerRepository.updateStatus(customerId, SubscriptionStatus.CANCELED)

        val defaultTier = tierRepository.findDefault()
        assignTierToUserUseCase.assignTierToUser(stripeCustomer.userId, defaultTier.id)

        logger.info { "Subscription ${subscription.id} deleted — user ${stripeCustomer.userId} downgraded to default tier" }
    }

    private fun mapStripeStatus(status: String?): SubscriptionStatus =
        when (status) {
            "active" -> SubscriptionStatus.ACTIVE
            "trialing" -> SubscriptionStatus.TRIALING
            "incomplete" -> SubscriptionStatus.INCOMPLETE
            "incomplete_expired" -> SubscriptionStatus.INCOMPLETE_EXPIRED
            "past_due" -> SubscriptionStatus.PAST_DUE
            "canceled" -> SubscriptionStatus.CANCELED
            "unpaid" -> SubscriptionStatus.UNPAID
            "paused" -> SubscriptionStatus.PAUSED
            else -> SubscriptionStatus.INCOMPLETE
        }
}
