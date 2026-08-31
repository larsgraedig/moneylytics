package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AcceptSuggestionUseCase
import com.moneylytics.api.application.port.input.RejectSuggestionUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.domain.CategorizationRequestedEvent
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.time.LocalDate

class CategorySuggestionControllerTest {
    private val organizationId = 1L
    private val exchange: ServerWebExchange = mock()
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase = ResolveOrganizationUseCase { _, _ -> organizationId }
    private val acceptSuggestionUseCase: AcceptSuggestionUseCase = mock()
    private val rejectSuggestionUseCase: RejectSuggestionUseCase = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()
    private val controller =
        CategorySuggestionController(
            acceptSuggestionUseCase,
            rejectSuggestionUseCase,
            resolveOrganizationUseCase,
            eventPublisher,
        )
    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()

    @Test
    fun `should publish CategorizationRequestedEvent on trigger`() =
        runTest {
            val response = controller.triggerSuggestions(principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            verify(eventPublisher).publishEvent(CategorizationRequestedEvent(organizationId))
        }

    @Test
    fun `should return 200 with updated transaction when accepting suggestion`() =
        runTest {
            val tx = tx(id = 5L, categoryId = 42L)
            whenever(acceptSuggestionUseCase.accept(5L, organizationId)).thenReturn(tx)

            val response = controller.acceptSuggestion(5L, principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.categoryId).isEqualTo(42L)
        }

    @Test
    fun `should return 404 when transaction not found on accept`() =
        runTest {
            whenever(acceptSuggestionUseCase.accept(any(), any())).thenReturn(null)

            val response = controller.acceptSuggestion(99L, principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should return 200 with updated transaction when rejecting suggestion`() =
        runTest {
            val tx = tx(id = 5L, excludeFromSuggestions = true)
            whenever(rejectSuggestionUseCase.reject(5L, organizationId)).thenReturn(tx)

            val response = controller.rejectSuggestion(5L, principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.excludeFromSuggestions).isTrue()
        }

    @Test
    fun `should return 404 when transaction not found on reject`() =
        runTest {
            whenever(rejectSuggestionUseCase.reject(any(), any())).thenReturn(null)

            val response = controller.rejectSuggestion(99L, principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    private fun tx(
        id: Long = 1L,
        categoryId: Long? = null,
        excludeFromSuggestions: Boolean = false,
    ) = Transaction(
        id = id,
        bookingDate = LocalDate.of(2025, 1, 1),
        valueDate = LocalDate.of(2025, 1, 1),
        accountingDate = LocalDate.of(2025, 1, 1),
        amount = BigDecimal("-50.00"),
        currency = "EUR",
        accountIban = "DE00TEST",
        categoryId = categoryId,
        excludeFromSuggestions = excludeFromSuggestions,
    )
}
