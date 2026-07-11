package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.domain.Budget
import com.moneylytics.api.domain.BudgetTransactionLink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class BudgetServiceTest {
    private val budgetRepository: BudgetRepository = mock()
    private val service = BudgetService(budgetRepository)

    private val userId = 1L
    private val urlaub = Budget(id = 1L, name = "Urlaub 2025", targetAmount = BigDecimal("1200"))
    private val kueche = Budget(id = 2L, name = "Neue Küche", targetAmount = BigDecimal("3500"))

    private val link1 = link(budgetId = 1L, amount = null, transactionAmount = BigDecimal("-380.00"), date = LocalDate.of(2025, 4, 10))
    private val link2 = link(budgetId = 1L, amount = null, transactionAmount = BigDecimal("-490.00"), date = LocalDate.of(2025, 5, 22))
    private val link3 =
        link(budgetId = 2L, amount = BigDecimal("500"), transactionAmount = BigDecimal("-800.00"), date = LocalDate.of(2025, 9, 5))

    @Test
    fun `should calculate total contributions with full amounts`() {
        whenever(budgetRepository.findAllByUserId(userId)).thenReturn(listOf(urlaub))
        whenever(budgetRepository.findAllTransactionLinksByUserId(userId)).thenReturn(listOf(link1, link2))

        val result = service.getBudgets(userId)

        assertThat(result).hasSize(1)
        assertThat(result[0].totalContributions).isEqualByComparingTo(BigDecimal("-870.00"))
    }

    @Test
    fun `should calculate total contributions with partial amounts`() {
        whenever(budgetRepository.findAllByUserId(userId)).thenReturn(listOf(kueche))
        whenever(budgetRepository.findAllTransactionLinksByUserId(userId)).thenReturn(listOf(link3))

        val result = service.getBudgets(userId)

        assertThat(result).hasSize(1)
        assertThat(result[0].totalContributions).isEqualByComparingTo(BigDecimal("-500.00"))
    }

    @Test
    fun `should build sorted cumulative chart points`() {
        whenever(budgetRepository.findAllByUserId(userId)).thenReturn(listOf(urlaub))
        whenever(budgetRepository.findAllTransactionLinksByUserId(userId)).thenReturn(listOf(link2, link1))

        val result = service.getBudgets(userId)

        val points = result[0].chartPoints
        assertThat(points).hasSize(2)
        assertThat(points[0].date).isEqualTo("2025-04-10")
        assertThat(points[0].cumulative).isEqualByComparingTo(BigDecimal("-380.00"))
        assertThat(points[1].date).isEqualTo("2025-05-22")
        assertThat(points[1].cumulative).isEqualByComparingTo(BigDecimal("-870.00"))
    }

    private fun link(
        budgetId: Long,
        amount: BigDecimal?,
        transactionAmount: BigDecimal,
        date: LocalDate,
    ) = BudgetTransactionLink(
        id = 1L,
        budgetId = budgetId,
        transactionId = 1L,
        amount = amount,
        transactionAmount = transactionAmount,
        transactionDate = date,
        transactionCategory = null,
        transactionSubcategory = null,
        transactionPurpose = null,
        transactionComment = null,
    )
}
