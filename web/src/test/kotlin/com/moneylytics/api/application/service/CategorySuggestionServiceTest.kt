package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.CategorizationRequestedEvent
import com.moneylytics.api.domain.CategoryClassifierFeatures
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class CategorySuggestionServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val categoryClassifier: CategoryClassifier = mock()
    private val service = CategorySuggestionService(transactionRepository, categoryClassifier)

    private val organizationId = 1L

    @Test
    fun `should store suggestions only for transactions where classifier returns a category`() {
        val tx1 = tx(id = 10L, purpose = "Amazon Prime")
        val tx2 = tx(id = 11L, purpose = "Unbekannt")
        whenever(transactionRepository.findUncategorizedForSuggestion(organizationId)).thenReturn(listOf(tx1, tx2))
        whenever(categoryClassifier.suggestAll(eq(organizationId), any<List<CategoryClassifierFeatures>>()))
            .thenReturn(listOf(42L, null))

        service.suggestForOrganization(organizationId)

        verify(transactionRepository).updateSuggestedCategory(10L, organizationId, 42L)
        verify(transactionRepository, never()).updateSuggestedCategory(eq(11L), any(), any())
    }

    @Test
    fun `should do nothing when no uncategorized transactions exist`() {
        whenever(transactionRepository.findUncategorizedForSuggestion(organizationId)).thenReturn(emptyList())

        service.suggestForOrganization(organizationId)

        verify(categoryClassifier, never()).suggestAll(any(), any<List<CategoryClassifierFeatures>>())
    }

    @Test
    fun `should delegate to repository when accepting suggestion`() {
        val tx = tx(id = 5L)
        whenever(transactionRepository.acceptSuggestion(5L, organizationId)).thenReturn(tx)

        val result = service.accept(5L, organizationId)

        assertThat(result).isEqualTo(tx)
        verify(transactionRepository).acceptSuggestion(5L, organizationId)
    }

    @Test
    fun `should delegate to repository when rejecting suggestion`() {
        val tx = tx(id = 5L)
        whenever(transactionRepository.rejectSuggestion(5L, organizationId)).thenReturn(tx)

        val result = service.reject(5L, organizationId)

        assertThat(result).isEqualTo(tx)
        verify(transactionRepository).rejectSuggestion(5L, organizationId)
    }

    @Test
    fun `should call suggestForOrganization when event is received`() {
        whenever(transactionRepository.findUncategorizedForSuggestion(organizationId)).thenReturn(emptyList())

        service.onCategorizationRequested(CategorizationRequestedEvent(organizationId))

        verify(transactionRepository).findUncategorizedForSuggestion(organizationId)
    }

    private fun tx(
        id: Long = 1L,
        purpose: String? = null,
    ) = Transaction(
        id = id,
        bookingDate = LocalDate.of(2025, 1, 1),
        valueDate = LocalDate.of(2025, 1, 1),
        accountingDate = LocalDate.of(2025, 1, 1),
        amount = BigDecimal("-50.00"),
        currency = "EUR",
        accountIban = "DE00TEST",
        purpose = purpose,
    )
}
