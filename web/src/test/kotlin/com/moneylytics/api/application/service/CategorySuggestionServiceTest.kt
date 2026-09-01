package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.CategoryClassifierFeatures
import com.moneylytics.api.domain.Transaction
import com.moneylytics.api.domain.TransactionsImportedEvent
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
    private val importId = 42L
    private val date = LocalDate.of(2025, 1, 15)

    private fun tx(
        id: Long,
        categoryId: Long? = null,
        purpose: String? = "Gehalt",
    ) = Transaction(
        id = id,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = BigDecimal("100.00"),
        currency = "EUR",
        accountIban = "DE01",
        categoryId = categoryId,
        purpose = purpose,
    )

    @Test
    fun `should write suggestions for uncategorized transactions`() {
        val tx1 = tx(id = 1L)
        val tx2 = tx(id = 2L)
        whenever(transactionRepository.findByIdsAndOrganizationId(setOf(1L, 2L), organizationId))
            .thenReturn(listOf(tx1, tx2))
        whenever(categoryClassifier.suggestAll(eq(organizationId), any())).thenReturn(listOf(10L, null))

        service.onTransactionsImported(TransactionsImportedEvent(organizationId, importId, listOf(1L, 2L)))

        verify(transactionRepository).updateSuggestedCategoryIds(listOf(1L to 10L))
    }

    @Test
    fun `should skip categorized transactions`() {
        val categorized = tx(id = 1L, categoryId = 5L)
        val uncategorized = tx(id = 2L)
        whenever(transactionRepository.findByIdsAndOrganizationId(setOf(1L, 2L), organizationId))
            .thenReturn(listOf(categorized, uncategorized))
        whenever(categoryClassifier.suggestAll(eq(organizationId), any())).thenReturn(listOf(7L))

        service.onTransactionsImported(TransactionsImportedEvent(organizationId, importId, listOf(1L, 2L)))

        verify(categoryClassifier).suggestAll(
            organizationId,
            listOf(
                CategoryClassifierFeatures(
                    purpose = uncategorized.purpose,
                    counterpartyName = uncategorized.counterpartyName,
                    counterpartyIban = uncategorized.counterpartyIban,
                    amount = uncategorized.amount,
                ),
            ),
        )
        verify(transactionRepository).updateSuggestedCategoryIds(listOf(2L to 7L))
    }

    @Test
    fun `should do nothing when all transactions are categorized`() {
        val tx1 = tx(id = 1L, categoryId = 3L)
        whenever(transactionRepository.findByIdsAndOrganizationId(setOf(1L), organizationId))
            .thenReturn(listOf(tx1))

        service.onTransactionsImported(TransactionsImportedEvent(organizationId, importId, listOf(1L)))

        verify(categoryClassifier, never()).suggestAll(any(), any())
        verify(transactionRepository, never()).updateSuggestedCategoryIds(any())
    }

    @Test
    fun `should skip transactions with excludeFromSuggestions set to true`() {
        val excluded = tx(id = 1L, purpose = "Gehalt").copy(excludeFromSuggestions = true)
        whenever(transactionRepository.findByIdsAndOrganizationId(setOf(1L), organizationId))
            .thenReturn(listOf(excluded))

        service.onTransactionsImported(TransactionsImportedEvent(organizationId, importId, listOf(1L)))

        verify(categoryClassifier, never()).suggestAll(any(), any())
        verify(transactionRepository, never()).updateSuggestedCategoryIds(any())
    }
}
