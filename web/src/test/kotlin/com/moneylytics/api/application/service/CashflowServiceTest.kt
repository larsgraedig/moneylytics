package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetCashflowQuery
import com.moneylytics.api.application.port.input.Granularity
import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import com.moneylytics.api.domain.TransactionOffsetLink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class CashflowServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val budgetRepository: BudgetRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val categoryClassifier: CategoryClassifier = mock()
    private val service = TransactionQueryService(transactionRepository, budgetRepository, categoryRepository, categoryClassifier)

    private val organizationId = 1L
    private val march = LocalDate.of(2025, 3, 1)
    private val marchEnd = LocalDate.of(2025, 3, 31)

    // March 2025 fixtures matching the plan's test data
    private val miete = tx(LocalDate.of(2025, 3, 1), BigDecimal("-950.00"))
    private val arzt =
        tx(LocalDate.of(2025, 3, 15), BigDecimal("-180.00"), offsetLink(BigDecimal("120"), BigDecimal("120"), BigDecimal("120.00")))
    private val erstattung =
        tx(LocalDate.of(2025, 3, 18), BigDecimal("120.00"), offsetLink(BigDecimal("120"), BigDecimal("120"), BigDecimal("-180.00")))
    private val gehalt = tx(LocalDate.of(2025, 3, 28), BigDecimal("2850.00"))

    @Test
    fun `should bucket transactions by month with correct gross and net income`() {
        whenever(transactionRepository.findByAccountingDateBetween(march, marchEnd, organizationId, null))
            .thenReturn(listOf(miete, arzt, erstattung, gehalt))

        val response = service.getCashflow(GetCashflowQuery(march, marchEnd, organizationId, Granularity.MONTHLY))

        val bucket = response.buckets.single { it.key == "2025-03" }
        assertThat(bucket.incomeGross).isEqualByComparingTo(BigDecimal("2970.00"))
        assertThat(bucket.incomeNet).isEqualByComparingTo(BigDecimal("2850.00"))
    }

    @Test
    fun `should bucket transactions by month with correct gross and net expenses`() {
        whenever(transactionRepository.findByAccountingDateBetween(march, marchEnd, organizationId, null))
            .thenReturn(listOf(miete, arzt, erstattung, gehalt))

        val response = service.getCashflow(GetCashflowQuery(march, marchEnd, organizationId, Granularity.MONTHLY))

        val bucket = response.buckets.single { it.key == "2025-03" }
        assertThat(bucket.expensesGross).isEqualByComparingTo(BigDecimal("1130.00"))
        assertThat(bucket.expensesNet).isEqualByComparingTo(BigDecimal("1010.00"))
        assertThat(bucket.net).isEqualByComparingTo(BigDecimal("1840.00"))
    }

    @Test
    fun `should fill empty months with zero values`() {
        val from = LocalDate.of(2025, 1, 1)
        val to = LocalDate.of(2025, 3, 31)
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(gehalt))

        val response = service.getCashflow(GetCashflowQuery(from, to, organizationId, Granularity.MONTHLY))

        assertThat(response.buckets).hasSize(3)
        val jan = response.buckets.single { it.key == "2025-01" }
        val feb = response.buckets.single { it.key == "2025-02" }
        assertThat(jan.incomeGross).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(jan.expensesGross).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(feb.incomeGross).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(feb.net).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `should aggregate by year when granularity is YEARLY`() {
        val from = LocalDate.of(2025, 1, 1)
        val to = LocalDate.of(2025, 12, 31)
        whenever(transactionRepository.findByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(listOf(miete, arzt, erstattung, gehalt))

        val response = service.getCashflow(GetCashflowQuery(from, to, organizationId, Granularity.YEARLY))

        assertThat(response.buckets).hasSize(1)
        val bucket = response.buckets.single { it.key == "2025" }
        assertThat(bucket.incomeGross).isEqualByComparingTo(BigDecimal("2970.00"))
        assertThat(bucket.expensesGross).isEqualByComparingTo(BigDecimal("1130.00"))
    }

    private fun tx(
        date: LocalDate,
        amount: BigDecimal,
        vararg links: TransactionOffsetLink,
    ) = Transaction(
        category = null,
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00TEST",
        offsetLinks = links.toList(),
    )

    private fun offsetLink(
        amountA: BigDecimal,
        amountB: BigDecimal,
        linkedAmount: BigDecimal,
    ) = TransactionOffsetLink(
        id = 1L,
        linkedTransactionId = 99L,
        linkedTransactionAmount = linkedAmount,
        amountA = amountA,
        amountB = amountB,
        myCommitted = amountA,
        groupId = null,
    )
}
