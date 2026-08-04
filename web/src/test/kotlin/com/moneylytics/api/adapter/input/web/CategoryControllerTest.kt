package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.DeleteCategoryUseCase
import com.moneylytics.api.application.port.input.FindOrCreateCategoryUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.GetCategoryStatsUseCase
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
    private val findOrCreateCategoryUseCase: FindOrCreateCategoryUseCase = mock()
    private val getCategoryStatsUseCase: GetCategoryStatsUseCase = mock()
    private val deleteCategoryUseCase: DeleteCategoryUseCase = mock()
    private val controller =
        CategoryController(
            getCategoriesUseCase,
            findOrCreateCategoryUseCase,
            getCategoryStatsUseCase,
            deleteCategoryUseCase,
            resolveOrganizationUseCase,
        )
    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()

    @Test
    fun `should return root categories sorted alphabetically`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(id = 1L, name = "Transport", parentId = null),
                    Category(id = 2L, name = "Lebensmittel", parentId = null),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            assertThat(response.categories.map { it.name }).containsExactly("Lebensmittel", "Transport")
        }

    @Test
    fun `should nest children under their parent`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(id = 1L, name = "Transport", parentId = null),
                    Category(id = 2L, name = "ÖPNV", parentId = 1L),
                    Category(id = 3L, name = "Auto", parentId = 1L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            assertThat(response.categories).hasSize(1)
            val transport = response.categories.single()
            assertThat(transport.name).isEqualTo("Transport")
            assertThat(transport.children.map { it.name }).containsExactly("Auto", "ÖPNV")
        }

    @Test
    fun `should support arbitrary depth nesting`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(id = 1L, name = "Lebensmittel", parentId = null),
                    Category(id = 2L, name = "Auswärts", parentId = 1L),
                    Category(id = 3L, name = "Restaurant", parentId = 2L),
                    Category(id = 4L, name = "Sushi", parentId = 3L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val root = response.categories.single()
            assertThat(root.name).isEqualTo("Lebensmittel")
            val auswaerts = root.children.single()
            assertThat(auswaerts.name).isEqualTo("Auswärts")
            val restaurant = auswaerts.children.single()
            assertThat(restaurant.name).isEqualTo("Restaurant")
            val sushi = restaurant.children.single()
            assertThat(sushi.name).isEqualTo("Sushi")
            assertThat(sushi.children).isEmpty()
        }

    @Test
    fun `should return empty response when no categories exist`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(emptyList())

            val response = controller.getCategories(principal, exchange)

            assertThat(response.categories).isEmpty()
        }

    @Test
    fun `should return created node when posting new category`() =
        runTest {
            val request = CreateCategoryRequest(path = listOf("Lebensmittel", "Supermarkt"))
            whenever(
                findOrCreateCategoryUseCase.findOrCreateCategory(listOf("Lebensmittel", "Supermarkt"), organizationId),
            ).thenReturn(Category(id = 42L, name = "Supermarkt", parentId = 1L))

            val response = controller.createCategory(request, principal, exchange)

            assertThat(response.id).isEqualTo(42L)
            assertThat(response.name).isEqualTo("Supermarkt")
            assertThat(response.children).isEmpty()
        }

    @Test
    fun `should sort children alphabetically within each node`() =
        runTest {
            whenever(getCategoriesUseCase.getCategories(organizationId)).thenReturn(
                listOf(
                    Category(id = 1L, name = "Freizeit", parentId = null),
                    Category(id = 2L, name = "Zirkus", parentId = 1L),
                    Category(id = 3L, name = "Ausstellung", parentId = 1L),
                    Category(id = 4L, name = "Museum", parentId = 1L),
                ),
            )

            val response = controller.getCategories(principal, exchange)

            val children = response.categories.single().children
            assertThat(children.map { it.name }).containsExactly("Ausstellung", "Museum", "Zirkus")
        }
}
