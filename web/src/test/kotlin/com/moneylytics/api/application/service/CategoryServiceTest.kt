package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.domain.Category
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CategoryServiceTest {
    private val categoryRepository: CategoryRepository = mock()
    private val service = CategoryService(categoryRepository)

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
}
