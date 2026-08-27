package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.StripeCustomerRepository
import com.moneylytics.api.application.port.output.StripeGateway
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.BillingInterval
import com.moneylytics.api.domain.StripeCustomer
import com.moneylytics.api.domain.SubscriptionSetupResult
import com.moneylytics.api.domain.SubscriptionStatus
import com.moneylytics.api.domain.Tier
import com.moneylytics.api.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionServiceTest {
    private val stripeGateway: StripeGateway = mock()
    private val stripeCustomerRepository: StripeCustomerRepository = mock()
    private val userRepository: UserRepository = mock()
    private val service = SubscriptionService(stripeGateway, stripeCustomerRepository, userRepository)

    private val userId = 1L
    private val tier = Tier(id = 1, name = "Standard", description = null, active = true, isDefault = true)
    private val user = User(id = userId, externalId = "user@example.com", passwordHash = null, tier = tier)
    private val stripeCustomerId = "cus_test123"
    private val subscriptionId = "sub_test456"
    private val clientSecret = "pi_secret_xyz"

    @Test
    fun `should create customer and subscription when no stripe customer exists`() {
        whenever(userRepository.findById(userId)).thenReturn(user)
        whenever(stripeCustomerRepository.findByUserId(userId)).thenReturn(null)
        whenever(stripeGateway.createCustomer(user.externalId)).thenReturn(stripeCustomerId)
        whenever(stripeCustomerRepository.save(any())).thenAnswer { it.arguments[0] as StripeCustomer }
        whenever(stripeGateway.createSubscription(stripeCustomerId, BillingInterval.MONTHLY)).thenReturn(
            SubscriptionSetupResult(
                subscriptionId = subscriptionId,
                clientSecret = clientSecret,
                currentPeriodStart = 1797408000L,
                currentPeriodEnd = 1800000000L,
                priceId = "price_monthly",
            ),
        )

        val result = service.createSubscription(userId, BillingInterval.MONTHLY)

        assertThat(result.clientSecret).isEqualTo(clientSecret)
        assertThat(result.subscriptionId).isEqualTo(subscriptionId)
        verify(stripeGateway).createCustomer(user.externalId)
        verify(stripeCustomerRepository).updateSubscription(
            stripeCustomerId = eq(stripeCustomerId),
            subscriptionId = eq(subscriptionId),
            status = eq(SubscriptionStatus.INCOMPLETE),
            currentPeriodStart = eq(1797408000L),
            currentPeriodEnd = eq(1800000000L),
            priceId = eq("price_monthly"),
        )
    }

    @Test
    fun `should reuse existing stripe customer when one exists`() {
        val existing =
            StripeCustomer(
                id = 1,
                userId = userId,
                stripeCustomerId = stripeCustomerId,
                stripeSubscriptionId = null,
                subscriptionStatus = null,
                currentPeriodStart = null,
                currentPeriodEnd = null,
                priceId = null,
            )
        whenever(userRepository.findById(userId)).thenReturn(user)
        whenever(stripeCustomerRepository.findByUserId(userId)).thenReturn(existing)
        whenever(stripeGateway.createSubscription(stripeCustomerId, BillingInterval.YEARLY)).thenReturn(
            SubscriptionSetupResult(
                subscriptionId = subscriptionId,
                clientSecret = clientSecret,
                currentPeriodStart = null,
                currentPeriodEnd = null,
                priceId = null,
            ),
        )

        val result = service.createSubscription(userId, BillingInterval.YEARLY)

        assertThat(result.clientSecret).isEqualTo(clientSecret)
        verify(stripeGateway, never()).createCustomer(any())
    }

    @Test
    fun `should cancel subscription when one exists`() {
        val existing =
            StripeCustomer(
                id = 1,
                userId = userId,
                stripeCustomerId = stripeCustomerId,
                stripeSubscriptionId = subscriptionId,
                subscriptionStatus = SubscriptionStatus.ACTIVE,
                currentPeriodStart = null,
                currentPeriodEnd = null,
                priceId = null,
            )
        whenever(stripeCustomerRepository.findByUserId(userId)).thenReturn(existing)

        service.cancelSubscription(userId)

        verify(stripeGateway).cancelSubscription(subscriptionId)
        verify(stripeCustomerRepository).updateStatus(stripeCustomerId, SubscriptionStatus.CANCELING)
    }

    @Test
    fun `should throw NoActiveSubscriptionException when no stripe customer exists on cancel`() {
        whenever(stripeCustomerRepository.findByUserId(userId)).thenReturn(null)

        assertThrows<NoActiveSubscriptionException> {
            service.cancelSubscription(userId)
        }
    }

    @Test
    fun `should throw NoActiveSubscriptionException when subscription id is missing on cancel`() {
        val existing =
            StripeCustomer(
                id = 1,
                userId = userId,
                stripeCustomerId = stripeCustomerId,
                stripeSubscriptionId = null,
                subscriptionStatus = null,
                currentPeriodStart = null,
                currentPeriodEnd = null,
                priceId = null,
            )
        whenever(stripeCustomerRepository.findByUserId(userId)).thenReturn(existing)

        assertThrows<NoActiveSubscriptionException> {
            service.cancelSubscription(userId)
        }
    }

    @Test
    fun `should return null status when no stripe customer exists`() {
        whenever(stripeCustomerRepository.findByUserId(userId)).thenReturn(null)

        val result = service.getSubscriptionStatus(userId)

        assertThat(result).isNull()
    }

    @Test
    fun `should return stripe customer when one exists`() {
        val existing =
            StripeCustomer(
                id = 1,
                userId = userId,
                stripeCustomerId = stripeCustomerId,
                stripeSubscriptionId = subscriptionId,
                subscriptionStatus = SubscriptionStatus.ACTIVE,
                currentPeriodStart = 1797408000L,
                currentPeriodEnd = 1800000000L,
                priceId = "price_monthly",
            )
        whenever(stripeCustomerRepository.findByUserId(userId)).thenReturn(existing)

        val result = service.getSubscriptionStatus(userId)

        assertThat(result).isNotNull
        assertThat(requireNotNull(result).subscriptionStatus).isEqualTo(SubscriptionStatus.ACTIVE)
    }
}
