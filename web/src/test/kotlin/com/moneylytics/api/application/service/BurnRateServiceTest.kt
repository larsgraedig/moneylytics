package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetBurnRateQuery
import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class BurnRateServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val budgetRepository: BudgetRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val service = TransactionQueryService(transactionRepository, budgetRepository, categoryRepository)

    private val organizationId = 1L
    private val june = LocalDate.of(2025, 6, 1)
    private val juneEnd = LocalDate.of(2025, 6, 30)

    // June 2025 fixtures: rollingWindow=3
    private val miete = tx(LocalDate.of(2025, 6, 1), BigDecimal("-950.00"))
    private val lebensmittel = tx(LocalDate.of(2025, 6, 3), BigDecimal("-45.50"))
    private val freizeit = tx(LocalDate.of(2025, 6, 5), BigDecimal("-120.00"))
    private val gehalt = tx(LocalDate.of(2025, 6, 28), BigDecimal("2900.00"))

    @Test
    fun `should aggregate expenses by date and fill gaps with zero`() {
        whenever(transactionRepository.findByAccountingDateBetween(june, juneEnd, organizationId, null))
            .thenReturn(listOf(miete, lebensmittel, freizeit, gehalt))

        val response = service.getBurnRate(GetBurnRateQuery(june, juneEnd, organizationId, rollingWindow = 3))

        assertThat(response.points).hasSize(30)
        assertThat(response.points[0].expenses).isEqualByComparingTo(BigDecimal("950.00"))
        assertThat(response.points[1].expenses).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(response.points[2].expenses).isEqualByComparingTo(BigDecimal("45.50"))
        assertThat(response.points[3].expenses).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(response.points[4].expenses).isEqualByComparingTo(BigDecimal("120.00"))
    }

    @Test
    fun `should compute rolling average over configurable window`() {
        whenever(transactionRepository.findByAccountingDateBetween(june, juneEnd, organizationId, null))
            .thenReturn(listOf(miete, lebensmittel, freizeit, gehalt))

        val response = service.getBurnRate(GetBurnRateQuery(june, juneEnd, organizationId, rollingWindow = 3))

        // Jun 01: 950/1 = 950.00
        assertThat(response.points[0].rollingAvg).isEqualByComparingTo(BigDecimal("950.00"))
        // Jun 02: (950+0)/2 = 475.00
        assertThat(response.points[1].rollingAvg).isEqualByComparingTo(BigDecimal("475.00"))
        // Jun 03: (950+0+45.50)/3 = 331.83
        assertThat(response.points[2].rollingAvg).isEqualByComparingTo(BigDecimal("331.83"))
        // Jun 04: (0+45.50+0)/3 = 15.17
        assertThat(response.points[3].rollingAvg).isEqualByComparingTo(BigDecimal("15.17"))
        // Jun 05: (45.50+0+120)/3 = 55.17
        assertThat(response.points[4].rollingAvg).isEqualByComparingTo(BigDecimal("55.17"))
    }

    @Test
    fun `should compute cumulative expenses and income`() {
        whenever(transactionRepository.findByAccountingDateBetween(june, juneEnd, organizationId, null))
            .thenReturn(listOf(miete, lebensmittel, freizeit, gehalt))

        val response = service.getBurnRate(GetBurnRateQuery(june, juneEnd, organizationId, rollingWindow = 3))

        // Jun 01: cumulative=950.00
        assertThat(response.points[0].cumulative).isEqualByComparingTo(BigDecimal("950.00"))
        // Jun 02: cumulative=950.00 (no expense)
        assertThat(response.points[1].cumulative).isEqualByComparingTo(BigDecimal("950.00"))
        // Jun 03: cumulative=995.50
        assertThat(response.points[2].cumulative).isEqualByComparingTo(BigDecimal("995.50"))
        // Jun 05: cumulative=1115.50
        assertThat(response.points[4].cumulative).isEqualByComparingTo(BigDecimal("1115.50"))
        // Jun 27 (index 26): cumulativeIncome still 0
        assertThat(response.points[26].cumulativeIncome).isEqualByComparingTo(BigDecimal.ZERO)
        // Jun 28 (index 27): cumulativeIncome = 2900
        assertThat(response.points[27].cumulativeIncome).isEqualByComparingTo(BigDecimal("2900.00"))
        assertThat(response.totalExpenses).isEqualByComparingTo(BigDecimal("1115.50"))
        assertThat(response.totalIncome).isEqualByComparingTo(BigDecimal("2900.00"))
        assertThat(response.avgPerDay).isEqualByComparingTo(BigDecimal("37.18"))
    }

    @Test
    fun `should exclude positive transactions from expenses and negative from income`() {
        val income = tx(LocalDate.of(2025, 6, 1), BigDecimal("500.00"))
        val expense = tx(LocalDate.of(2025, 6, 1), BigDecimal("-200.00"))
        whenever(transactionRepository.findByAccountingDateBetween(june, juneEnd, organizationId, null))
            .thenReturn(listOf(income, expense))

        val response = service.getBurnRate(GetBurnRateQuery(june, juneEnd, organizationId, rollingWindow = 7))

        assertThat(response.totalExpenses).isEqualByComparingTo(BigDecimal("200.00"))
        assertThat(response.totalIncome).isEqualByComparingTo(BigDecimal("500.00"))
        assertThat(response.points[0].expenses).isEqualByComparingTo(BigDecimal("200.00"))
        assertThat(response.points[0].cumulativeIncome).isEqualByComparingTo(BigDecimal("500.00"))
    }

    private fun tx(
        date: LocalDate,
        amount: BigDecimal,
    ) = Transaction(
        category = null,
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00TEST",
    )
}
