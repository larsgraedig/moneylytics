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
    fun `should return all categories for user`() {
        val categories = listOf(Category(name = "Lebensmittel", subcategory = "Supermarkt", group = null))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(categories)

        val result = service.getCategories(organizationId)

        assertThat(result).isEqualTo(categories)
    }

    @Test
    fun `should delegate save to repository`() {
        val categories = listOf(Category(name = "Lebensmittel", subcategory = "Supermarkt", group = null))

        service.saveCategories(categories, organizationId)

        verify(categoryRepository).saveAllIfAbsent(categories, organizationId)
    }

    @Test
    fun `should delegate findOrCreate to repository`() {
        val expected = Category(name = "Lebensmittel", subcategory = "Auswärts", group = "Restaurant", id = 42L)
        whenever(categoryRepository.findOrCreate("Lebensmittel", "Auswärts", "Restaurant", organizationId)).thenReturn(expected)

        val result = service.findOrCreateCategory("Lebensmittel", "Auswärts", "Restaurant", organizationId)

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `should pass null group to repository when creating subcategory-only entry`() {
        val expected = Category(name = "Transport", subcategory = "ÖPNV", group = null, id = 7L)
        whenever(categoryRepository.findOrCreate("Transport", "ÖPNV", null, organizationId)).thenReturn(expected)

        val result = service.findOrCreateCategory("Transport", "ÖPNV", null, organizationId)

        assertThat(result).isEqualTo(expected)
    }
}
