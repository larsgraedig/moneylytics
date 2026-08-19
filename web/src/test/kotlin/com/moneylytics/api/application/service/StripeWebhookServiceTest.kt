package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.AssignTierToUserUseCase
import com.moneylytics.api.application.port.output.InvoiceRepository
import com.moneylytics.api.application.port.output.StripeCustomerRepository
import com.moneylytics.api.application.port.output.StripeGateway
import com.moneylytics.api.application.port.output.TierRepository
import com.moneylytics.api.config.StripeProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class StripeWebhookServiceTest {
    private val stripeProperties =
        StripeProperties(
            secretKey = "sk_test_dummy",
            webhookSecret = "whsec_test",
            priceIdMonthly = "price_monthly",
            priceIdYearly = "price_yearly",
            publishableKey = "pk_test_dummy",
        )
    private val stripeCustomerRepository: StripeCustomerRepository = mock()
    private val invoiceRepository: InvoiceRepository = mock()
    private val stripeGateway: StripeGateway = mock()
    private val tierRepository: TierRepository = mock()
    private val assignTierToUserUseCase: AssignTierToUserUseCase = mock()

    private val service =
        StripeWebhookService(
            stripeProperties = stripeProperties,
            stripeCustomerRepository = stripeCustomerRepository,
            invoiceRepository = invoiceRepository,
            stripeGateway = stripeGateway,
            tierRepository = tierRepository,
            assignTierToUserUseCase = assignTierToUserUseCase,
        )

    @Test
    fun `should return BAD_REQUEST when stripe signature is invalid`() {
        val ex =
            assertThrows<ResponseStatusException> {
                service.handle(payload = """{"type":"invoice.paid"}""", signature = "t=invalid,v1=invalid")
            }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `should return BAD_REQUEST when payload is empty and signature is garbage`() {
        val ex =
            assertThrows<ResponseStatusException> {
                service.handle(payload = "", signature = "garbage")
            }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }
}
