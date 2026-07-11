package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetCategoryTotalsQuery
import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class CategoryTotalsServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val budgetRepository: BudgetRepository = mock()
    private val service = TransactionQueryService(transactionRepository, budgetRepository)

    private val userId = 1L
    private val from = LocalDate.of(2025, 1, 1)
    private val to = LocalDate.of(2025, 1, 31)

    private val supermarkt = tx(category = "Lebensmittel", subcategory = "Supermarkt", amount = BigDecimal("-80.00"))
    private val restaurant = tx(category = "Lebensmittel", subcategory = "Restaurant", amount = BigDecimal("-45.00"))
    private val oepnv = tx(category = "Transport", subcategory = "ÖPNV", amount = BigDecimal("-86.00"))
    private val streaming = tx(category = "Freizeit", subcategory = "Streaming", amount = BigDecimal("-18.00"))
    private val income = tx(category = "Einnahmen", subcategory = "Gehalt", amount = BigDecimal("2500.00"))

    @Test
    fun `should return category totals sorted descending by value`() {
        whenever(transactionRepository.findByAccountingDateBetween(from, to, userId, null))
            .thenReturn(listOf(supermarkt, restaurant, oepnv, streaming, income))

        val response = service.getCategoryTotals(GetCategoryTotalsQuery(from, to, userId))

        assertThat(response.items).hasSize(3)
        assertThat(response.items[0].name).isEqualTo("Lebensmittel")
        assertThat(response.items[0].value).isEqualByComparingTo(BigDecimal("125.00"))
        assertThat(response.items[1].name).isEqualTo("Transport")
        assertThat(response.items[1].value).isEqualByComparingTo(BigDecimal("86.00"))
        assertThat(response.items[2].name).isEqualTo("Freizeit")
        assertThat(response.items[2].value).isEqualByComparingTo(BigDecimal("18.00"))
    }

    @Test
    fun `should return subcategory totals when category filter is applied`() {
        whenever(transactionRepository.findByAccountingDateBetween(from, to, userId, null))
            .thenReturn(listOf(supermarkt, restaurant, oepnv, streaming))

        val response = service.getCategoryTotals(GetCategoryTotalsQuery(from, to, userId, category = "Lebensmittel"))

        assertThat(response.items).hasSize(2)
        assertThat(response.items[0].name).isEqualTo("Supermarkt")
        assertThat(response.items[0].value).isEqualByComparingTo(BigDecimal("80.00"))
        assertThat(response.items[1].name).isEqualTo("Restaurant")
        assertThat(response.items[1].value).isEqualByComparingTo(BigDecimal("45.00"))
    }

    @Test
    fun `should exclude income transactions`() {
        whenever(transactionRepository.findByAccountingDateBetween(from, to, userId, null))
            .thenReturn(listOf(supermarkt, income))

        val response = service.getCategoryTotals(GetCategoryTotalsQuery(from, to, userId))

        assertThat(response.items).hasSize(1)
        assertThat(response.items[0].name).isEqualTo("Lebensmittel")
    }

    private fun tx(
        category: String,
        subcategory: String,
        amount: BigDecimal,
    ) = Transaction(
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
