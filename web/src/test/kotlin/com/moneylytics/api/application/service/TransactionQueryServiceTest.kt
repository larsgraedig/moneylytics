package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.TransactionType
import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class TransactionQueryServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val budgetRepository: BudgetRepository = mock()
    private val service = TransactionQueryService(transactionRepository, budgetRepository)

    private val organizationId = 1L
    private val from = LocalDate.of(2025, 1, 1)
    private val to = LocalDate.of(2025, 1, 31)

    private val gehalt = tx(BigDecimal("2500.00"), category = "Einnahmen", subcategory = "Gehalt")
    private val miete = tx(BigDecimal("-950.00"), category = "Wohnen", subcategory = "Miete")
    private val lebensmittel = tx(BigDecimal("-80.00"), category = "Lebensmittel", subcategory = "Supermarkt")
    private val uncategorized = tx(BigDecimal("-30.00"), category = null, subcategory = null)

    @Test
    fun `should filter income transactions`() {
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(gehalt, miete, lebensmittel))

        val result = service.getTransactions(GetTransactionsQuery(from, to, organizationId, type = TransactionType.INCOME))

        assertThat(result).containsExactly(gehalt)
    }

    @Test
    fun `should filter expense transactions`() {
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(gehalt, miete, lebensmittel))

        val result = service.getTransactions(GetTransactionsQuery(from, to, organizationId, type = TransactionType.EXPENSES))

        assertThat(result).containsExactlyInAnyOrder(miete, lebensmittel)
    }

    @Test
    fun `should filter uncategorized transactions`() {
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(miete, lebensmittel, uncategorized))

        val result = service.getTransactions(GetTransactionsQuery(from, to, organizationId, uncategorized = true))

        assertThat(result).containsExactly(uncategorized)
    }

    @Test
    fun `should filter by category`() {
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(miete, lebensmittel))

        val result = service.getTransactions(GetTransactionsQuery(from, to, organizationId, category = "Lebensmittel"))

        assertThat(result).containsExactly(lebensmittel)
    }

    @Test
    fun `should filter by subcategory`() {
        val restaurant = tx(BigDecimal("-45.00"), category = "Lebensmittel", subcategory = "Restaurant")
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(lebensmittel, restaurant))

        val result = service.getTransactions(GetTransactionsQuery(from, to, organizationId, subcategory = "Supermarkt"))

        assertThat(result).containsExactly(lebensmittel)
    }

    @Test
    fun `should exclude transactions already assigned to a collection`() {
        val txInCollection = tx(BigDecimal("-40.00"), id = 5L)
        val txFree = tx(BigDecimal("-60.00"), id = 6L)
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(txInCollection, txFree))
        whenever(transactionRepository.findAssignedTransactionIdsByCollectionId(99L)).thenReturn(setOf(5L))

        val result = service.getTransactions(GetTransactionsQuery(from, to, organizationId, excludeCollectionId = 99L))

        assertThat(result).containsExactly(txFree)
    }

    @Test
    fun `should exclude transactions already assigned to a budget`() {
        val txInBudget = tx(BigDecimal("-40.00"), id = 7L)
        val txFree = tx(BigDecimal("-60.00"), id = 8L)
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(txInBudget, txFree))
        whenever(budgetRepository.findAssignedTransactionIdsByBudgetId(11L, organizationId)).thenReturn(setOf(7L))

        val result = service.getTransactions(GetTransactionsQuery(from, to, organizationId, excludeBudgetId = 11L))

        assertThat(result).containsExactly(txFree)
    }

    private fun tx(
        amount: BigDecimal,
        category: String? = null,
        subcategory: String? = null,
        id: Long? = null,
    ) = Transaction(
        id = id,
        category = category,
        subcategory = subcategory,
        bookingDate = from,
        valueDate = from,
        accountingDate = from,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00TEST",
    )
}
