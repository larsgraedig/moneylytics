package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetCategoryTotalsQuery
import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Category
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
    private val categoryRepository: CategoryRepository = mock()
    private val categoryClassifier: CategoryClassifier = mock()
    private val service = TransactionQueryService(transactionRepository, budgetRepository, categoryRepository, categoryClassifier)

    private val organizationId = 1L
    private val from = LocalDate.of(2025, 1, 1)
    private val to = LocalDate.of(2025, 1, 31)

    private val supermarkt = tx(category = "Lebensmittel", group = "Supermarkt", amount = BigDecimal("-80.00"))
    private val restaurant = tx(category = "Lebensmittel", group = "Restaurant", amount = BigDecimal("-45.00"))
    private val oepnv = tx(category = "Transport", group = "ÖPNV", amount = BigDecimal("-86.00"))
    private val streaming = tx(category = "Freizeit", group = "Streaming", amount = BigDecimal("-18.00"))
    private val income = tx(category = "Einnahmen", group = "Gehalt", amount = BigDecimal("2500.00"))

    @Test
    fun `should return category totals sorted descending by value`() {
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(supermarkt, restaurant, oepnv, streaming, income))

        val response = service.getCategoryTotals(GetCategoryTotalsQuery(from, to, organizationId))

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
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(supermarkt, restaurant, oepnv, streaming))

        val response = service.getCategoryTotals(GetCategoryTotalsQuery(from, to, organizationId, category = "Lebensmittel"))

        assertThat(response.items).hasSize(2)
        assertThat(response.items[0].name).isEqualTo("Supermarkt")
        assertThat(response.items[0].value).isEqualByComparingTo(BigDecimal("80.00"))
        assertThat(response.items[1].name).isEqualTo("Restaurant")
        assertThat(response.items[1].value).isEqualByComparingTo(BigDecimal("45.00"))
    }

    @Test
    fun `should exclude income transactions`() {
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(supermarkt, income))

        val response = service.getCategoryTotals(GetCategoryTotalsQuery(from, to, organizationId))

        assertThat(response.items).hasSize(1)
        assertThat(response.items[0].name).isEqualTo("Lebensmittel")
    }

    @Test
    fun `should group by direct children and return their totals when categoryId is provided`() {
        val parent = Category(id = 10L, name = "Wohnen", parentId = null)
        val miete = Category(id = 11L, name = "Miete", parentId = 10L)
        val strom = Category(id = 12L, name = "Strom", parentId = 10L)
        whenever(categoryRepository.findAll(organizationId)).thenReturn(listOf(parent, miete, strom))
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(
                listOf(
                    tx(category = "Wohnen", group = "Miete", amount = BigDecimal("-800.00"), categoryId = 11L),
                    tx(category = "Wohnen", group = "Strom", amount = BigDecimal("-120.00"), categoryId = 12L),
                ),
            )

        val response = service.getCategoryTotals(GetCategoryTotalsQuery(from, to, organizationId, categoryId = 10L))

        assertThat(response.items).hasSize(2)
        assertThat(response.items[0].name).isEqualTo("Miete")
        assertThat(response.items[0].value).isEqualByComparingTo(BigDecimal("800.00"))
        assertThat(response.items[0].categoryId).isEqualTo(11L)
        assertThat(response.items[1].name).isEqualTo("Strom")
        assertThat(response.items[1].value).isEqualByComparingTo(BigDecimal("120.00"))
        assertThat(response.items[1].categoryId).isEqualTo(12L)
    }

    @Test
    fun `should return empty list when selected category has no children with transactions`() {
        val leaf = Category(id = 11L, name = "Miete", parentId = 10L)
        whenever(categoryRepository.findAll(organizationId)).thenReturn(listOf(leaf))
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(emptyList())

        val response = service.getCategoryTotals(GetCategoryTotalsQuery(from, to, organizationId, categoryId = 11L))

        assertThat(response.items).isEmpty()
    }

    private fun tx(
        category: String,
        group: String,
        amount: BigDecimal,
        categoryId: Long? = null,
    ) = Transaction(
        category = category,
        group = group,
        bookingDate = from,
        valueDate = from,
        accountingDate = from,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00TEST",
        categoryId = categoryId,
    )
}
