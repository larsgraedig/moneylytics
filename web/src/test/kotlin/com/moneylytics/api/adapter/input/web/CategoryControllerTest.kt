package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.domain.Category
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.User
import org.springframework.web.server.ServerWebExchange

class CategoryControllerTest {
    private val organizationId = 1L
    private val exchange: ServerWebExchange = mock()
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase = ResolveOrganizationUseCase { _, _ -> organizationId }
    private val getCategoriesUseCase: GetCategoriesUseCase = mock()
    private val controller = CategoryController(getCategoriesUseCase, resolveOrganizationUseCase)
    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()

    @Test
    fun `should group categories by category name`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Transport", subcategory = null, group = "ÖPNV", id = 1L),
                    Category(name = "Transport", subcategory = null, group = "Auto", id = 2L),
                    Category(name = "Lebensmittel", subcategory = null, group = "Supermarkt", id = 3L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            assertThat(response.categories).hasSize(2)
            assertThat(response.categories.map { it.name }).containsExactly("Lebensmittel", "Transport")
        }

    @Test
    fun `should sort categories alphabetically`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Wohnen", subcategory = null, group = "Miete", id = 1L),
                    Category(name = "Auto", subcategory = null, group = "Versicherung", id = 2L),
                    Category(name = "Lebensmittel", subcategory = null, group = "Supermarkt", id = 3L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            assertThat(response.categories.map { it.name }).containsExactly("Auto", "Lebensmittel", "Wohnen")
        }

    @Test
    fun `should put groups with subcategory into CategorySubGroupResponse`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Lebensmittel", subcategory = "Auswärts", group = "Restaurant", id = 1L),
                    Category(name = "Lebensmittel", subcategory = "Auswärts", group = "Lieferdienst", id = 2L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val lebensmittel = response.categories.single()
            assertThat(lebensmittel.subcategories).hasSize(1)
            assertThat(lebensmittel.subcategories[0].name).isEqualTo("Auswärts")
            assertThat(lebensmittel.subcategories[0].groups.map { it.name }).containsExactly("Lieferdienst", "Restaurant")
            assertThat(lebensmittel.directGroups).isEmpty()
        }

    @Test
    fun `should put groups without subcategory into directGroups list`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Transport", subcategory = null, group = "ÖPNV", id = 1L),
                    Category(name = "Transport", subcategory = null, group = "Auto", id = 2L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val transport = response.categories.single()
            assertThat(transport.directGroups.map { it.name }).containsExactly("Auto", "ÖPNV")
            assertThat(transport.subcategories).isEmpty()
        }

    @Test
    fun `should separate subcategory-grouped and direct groups within same category`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Lebensmittel", subcategory = null, group = "Supermarkt", id = 1L),
                    Category(name = "Lebensmittel", subcategory = "Auswärts", group = "Restaurant", id = 2L),
                    Category(name = "Lebensmittel", subcategory = "Auswärts", group = "Lieferdienst", id = 3L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val lebensmittel = response.categories.single()
            assertThat(lebensmittel.directGroups.map { it.name }).containsExactly("Supermarkt")
            assertThat(lebensmittel.subcategories).hasSize(1)
            assertThat(lebensmittel.subcategories[0].groups.map { it.name }).containsExactly("Lieferdienst", "Restaurant")
        }

    @Test
    fun `should sort groups within subcategories alphabetically`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Freizeit", subcategory = "Kultur", group = "Zirkus", id = 1L),
                    Category(name = "Freizeit", subcategory = "Kultur", group = "Ausstellung", id = 2L),
                    Category(name = "Freizeit", subcategory = "Kultur", group = "Museum", id = 3L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val sub =
                response.categories
                    .single()
                    .subcategories
                    .single()
            assertThat(sub.groups.map { it.name }).containsExactly("Ausstellung", "Museum", "Zirkus")
        }

    @Test
    fun `should sort subcategories within a category alphabetically`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Freizeit", subcategory = "Unterhaltung", group = "Kino", id = 1L),
                    Category(name = "Freizeit", subcategory = "Bildung", group = "Museum", id = 2L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val subcategories = response.categories.single().subcategories
            assertThat(subcategories.map { it.name }).containsExactly("Bildung", "Unterhaltung")
        }

    @Test
    fun `should return empty response when no categories exist`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(emptyList())

            val response = controller.getCategories(principal, exchange)

            assertThat(response.categories).isEmpty()
        }
}
