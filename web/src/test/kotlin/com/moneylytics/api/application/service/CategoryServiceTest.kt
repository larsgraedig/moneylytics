package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetCategoryStatsQuery
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Category
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class CategoryServiceTest {
    private val categoryRepository: CategoryRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val service = CategoryService(categoryRepository, transactionRepository)

    private val organizationId = 1L

    @Test
    fun `should return all categories for organization`() {
        val categories = listOf(Category(id = 1L, name = "Lebensmittel", parentId = null))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(categories)

        val result = service.getCategories(organizationId)

        assertThat(result).isEqualTo(categories)
    }

    @Test
    fun `should delegate findOrCreate to repository`() {
        val path = listOf("Lebensmittel", "Supermarkt")
        val expected = Category(id = 42L, name = "Supermarkt", parentId = 1L)
        whenever(categoryRepository.findOrCreate(path, organizationId)).thenReturn(expected)

        val result = service.findOrCreateCategory(path, organizationId)

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `should pass single-element path for root category`() {
        val path = listOf("Transport")
        val expected = Category(id = 7L, name = "Transport", parentId = null)
        whenever(categoryRepository.findOrCreate(path, organizationId)).thenReturn(expected)

        val result = service.findOrCreateCategory(path, organizationId)

        assertThat(result).isEqualTo(expected)
        verify(categoryRepository).findOrCreate(path, organizationId)
    }

    @Test
    fun `should combine total and period counts per category`() {
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 3, 31)
        whenever(transactionRepository.countByCategoryGrouped(organizationId, null))
            .thenReturn(mapOf(1L to 10L, 2L to 5L))
        whenever(transactionRepository.countByCategoryGroupedInPeriod(organizationId, from, to, null))
            .thenReturn(mapOf(1L to 3L))

        val result = service.getCategoryStats(GetCategoryStatsQuery(organizationId, from, to))

        assertThat(result).hasSize(2)
        val cat1 = result.first { it.categoryId == 1L }
        assertThat(cat1.totalCount).isEqualTo(10L)
        assertThat(cat1.periodCount).isEqualTo(3L)
        val cat2 = result.first { it.categoryId == 2L }
        assertThat(cat2.totalCount).isEqualTo(5L)
        assertThat(cat2.periodCount).isEqualTo(0L)
    }

    @Test
    fun `should include categories that only appear in period counts`() {
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 3, 31)
        whenever(transactionRepository.countByCategoryGrouped(organizationId, null))
            .thenReturn(emptyMap())
        whenever(transactionRepository.countByCategoryGroupedInPeriod(organizationId, from, to, null))
            .thenReturn(mapOf(99L to 2L))

        val result = service.getCategoryStats(GetCategoryStatsQuery(organizationId, from, to))

        assertThat(result).hasSize(1)
        assertThat(result.first().categoryId).isEqualTo(99L)
        assertThat(result.first().totalCount).isEqualTo(0L)
        assertThat(result.first().periodCount).isEqualTo(2L)
    }
}
