package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.CalendarSumsQuery
import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class CalendarSumsServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val budgetRepository: BudgetRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val categoryClassifier: CategoryClassifier = mock()
    private val service = TransactionQueryService(transactionRepository, budgetRepository, categoryRepository, categoryClassifier)

    private val organizationId = 1L
    private val from = LocalDate.of(2025, 3, 1)
    private val to = LocalDate.of(2025, 3, 31)

    @Test
    fun `should return one entry per day that has expenses`() {
        val day3 = LocalDate.of(2025, 3, 3)
        val day15 = LocalDate.of(2025, 3, 15)
        whenever(transactionRepository.sumExpensesByDay(from, to, organizationId, null))
            .thenReturn(mapOf(day3 to BigDecimal("120.00"), day15 to BigDecimal("380.00")))

        val response = service.getCalendarSums(CalendarSumsQuery(from, to, organizationId))

        assertThat(response.data).hasSize(2)
        val entry3 = response.data.single { it.day == "2025-03-03" }
        assertThat(entry3.value).isEqualByComparingTo(BigDecimal("120.00"))
        val entry15 = response.data.single { it.day == "2025-03-15" }
        assertThat(entry15.value).isEqualByComparingTo(BigDecimal("380.00"))
    }

    @Test
    fun `should return empty list when there are no expenses`() {
        whenever(transactionRepository.sumExpensesByDay(from, to, organizationId, null))
            .thenReturn(emptyMap())

        val response = service.getCalendarSums(CalendarSumsQuery(from, to, organizationId))

        assertThat(response.data).isEmpty()
    }
}
