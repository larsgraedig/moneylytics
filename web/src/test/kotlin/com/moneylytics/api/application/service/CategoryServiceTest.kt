package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetCategoryStatsQuery
import com.moneylytics.api.application.port.input.MoveCategoryCommand
import com.moneylytics.api.application.port.input.RenameCategoryCommand
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Category
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class CategoryServiceTest {
    private val categoryRepository: CategoryRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val thresholdRepository: ThresholdRepository = mock()
    private val service = CategoryService(categoryRepository, transactionRepository, thresholdRepository)

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
    fun `should delegate delete to repository when no threshold exists`() {
        whenever(thresholdRepository.existsByCategoryId(42L, organizationId)).thenReturn(false)

        service.deleteCategory(42L, organizationId)

        verify(categoryRepository).delete(42L, organizationId)
    }

    @Test
    fun `should throw when threshold exists for category`() {
        whenever(thresholdRepository.existsByCategoryId(42L, organizationId)).thenReturn(true)

        org.assertj.core.api.Assertions
            .assertThatThrownBy { service.deleteCategory(42L, organizationId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("THRESHOLD_EXISTS")
        verify(categoryRepository, org.mockito.kotlin.never()).delete(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `should propagate exception when category not found on delete`() {
        whenever(thresholdRepository.existsByCategoryId(99L, organizationId)).thenReturn(false)
        whenever(categoryRepository.delete(99L, organizationId)).thenThrow(NoSuchElementException("not found"))

        assertThatThrownBy { service.deleteCategory(99L, organizationId) }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `should move category to new parent`() {
        whenever(categoryRepository.findAll(organizationId)).thenReturn(
            listOf(
                Category(id = 1L, name = "Root", parentId = null),
                Category(id = 2L, name = "Child", parentId = 1L),
            ),
        )

        service.moveCategory(MoveCategoryCommand(id = 1L, newParentId = null, organizationId = organizationId))

        verify(categoryRepository).move(1L, null, organizationId)
    }

    @Test
    fun `should move root category under another category`() {
        whenever(categoryRepository.findAll(organizationId)).thenReturn(
            listOf(
                Category(id = 1L, name = "Freizeit", parentId = null),
                Category(id = 2L, name = "Essen", parentId = null),
            ),
        )

        service.moveCategory(MoveCategoryCommand(id = 1L, newParentId = 2L, organizationId = organizationId))

        verify(categoryRepository).move(1L, 2L, organizationId)
    }

    @Test
    fun `should throw when moving category into its own descendant`() {
        whenever(categoryRepository.findAll(organizationId)).thenReturn(
            listOf(
                Category(id = 1L, name = "Root", parentId = null),
                Category(id = 2L, name = "Child", parentId = 1L),
                Category(id = 3L, name = "Grandchild", parentId = 2L),
            ),
        )

        assertThatThrownBy {
            service.moveCategory(MoveCategoryCommand(id = 1L, newParentId = 3L, organizationId = organizationId))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("CIRCULAR_REFERENCE")
        verify(categoryRepository, never()).move(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `should throw when moving category onto itself`() {
        whenever(categoryRepository.findAll(organizationId)).thenReturn(
            listOf(Category(id = 1L, name = "Root", parentId = null)),
        )

        assertThatThrownBy {
            service.moveCategory(MoveCategoryCommand(id = 1L, newParentId = 1L, organizationId = organizationId))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("CIRCULAR_REFERENCE")
    }

    @Test
    fun `should rename category with trimmed name`() {
        service.renameCategory(RenameCategoryCommand(id = 1L, newName = "  Neuer Name  ", organizationId = organizationId))

        verify(categoryRepository).rename(1L, "Neuer Name", organizationId)
    }

    @Test
    fun `should throw when new name is blank`() {
        assertThatThrownBy {
            service.renameCategory(RenameCategoryCommand(id = 1L, newName = "  ", organizationId = organizationId))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("EMPTY_NAME")
        verify(categoryRepository, never()).rename(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
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
