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
    fun `should group subcategories by category name`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Transport", subcategory = "ÖPNV"),
                    Category(name = "Transport", subcategory = "Auto"),
                    Category(name = "Lebensmittel", subcategory = "Supermarkt"),
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
                    Category(name = "Wohnen", subcategory = "Miete"),
                    Category(name = "Auto", subcategory = "Versicherung"),
                    Category(name = "Lebensmittel", subcategory = "Supermarkt"),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            assertThat(response.categories.map { it.name }).containsExactly("Auto", "Lebensmittel", "Wohnen")
        }

    @Test
    fun `should put subcategories with group into CategorySubGroupResponse`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Lebensmittel", subcategory = "Restaurant", group = "Auswärts"),
                    Category(name = "Lebensmittel", subcategory = "Lieferdienst", group = "Auswärts"),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val lebensmittel = response.categories.single()
            assertThat(lebensmittel.groups).hasSize(1)
            assertThat(lebensmittel.groups[0].name).isEqualTo("Auswärts")
            assertThat(lebensmittel.groups[0].subcategories).containsExactly("Lieferdienst", "Restaurant")
            assertThat(lebensmittel.subcategories).isEmpty()
        }

    @Test
    fun `should put subcategories without group into flat subcategories list`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Transport", subcategory = "ÖPNV"),
                    Category(name = "Transport", subcategory = "Auto"),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val transport = response.categories.single()
            assertThat(transport.subcategories).containsExactly("Auto", "ÖPNV")
            assertThat(transport.groups).isEmpty()
        }

    @Test
    fun `should separate grouped and ungrouped subcategories within same category`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Lebensmittel", subcategory = "Supermarkt"),
                    Category(name = "Lebensmittel", subcategory = "Restaurant", group = "Auswärts"),
                    Category(name = "Lebensmittel", subcategory = "Lieferdienst", group = "Auswärts"),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val lebensmittel = response.categories.single()
            assertThat(lebensmittel.subcategories).containsExactly("Supermarkt")
            assertThat(lebensmittel.groups).hasSize(1)
            assertThat(lebensmittel.groups[0].subcategories).containsExactly("Lieferdienst", "Restaurant")
        }

    @Test
    fun `should sort subcategories within groups alphabetically`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Freizeit", subcategory = "Zirkus", group = "Kultur"),
                    Category(name = "Freizeit", subcategory = "Ausstellung", group = "Kultur"),
                    Category(name = "Freizeit", subcategory = "Museum", group = "Kultur"),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val group =
                response.categories
                    .single()
                    .groups
                    .single()
            assertThat(group.subcategories).containsExactly("Ausstellung", "Museum", "Zirkus")
        }

    @Test
    fun `should sort groups within a category alphabetically`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(name = "Freizeit", subcategory = "Kino", group = "Unterhaltung"),
                    Category(name = "Freizeit", subcategory = "Museum", group = "Bildung"),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val groups = response.categories.single().groups
            assertThat(groups.map { it.name }).containsExactly("Bildung", "Unterhaltung")
        }

    @Test
    fun `should return empty response when no categories exist`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(emptyList())

            val response = controller.getCategories(principal, exchange)

            assertThat(response.categories).isEmpty()
        }
}
