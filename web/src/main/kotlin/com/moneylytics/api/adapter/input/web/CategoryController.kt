package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.FindOrCreateCategoryUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.domain.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

data class CreateCategoryRequest(
    val path: List<String>,
)

@RestController
@RequestMapping("/categories")
class CategoryController(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val findOrCreateCategoryUseCase: FindOrCreateCategoryUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    @GetMapping
    suspend fun getCategories(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): CategoriesResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val tree =
            withContext(Dispatchers.IO) {
                val nodes = getCategoriesUseCase.getCategories(organizationId)
                buildTree(nodes)
            }
        return CategoriesResponse(categories = tree)
    }

    @PostMapping
    suspend fun createCategory(
        @RequestBody request: CreateCategoryRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): CategoryNodeResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val category =
            withContext(Dispatchers.IO) {
                findOrCreateCategoryUseCase.findOrCreateCategory(
                    path = request.path,
                    organizationId = organizationId,
                )
            }
        return CategoryNodeResponse(id = requireNotNull(category.id), name = category.name, children = emptyList())
    }

    private fun buildTree(nodes: List<Category>): List<CategoryNodeResponse> {
        val childrenByParentId = nodes.groupBy { it.parentId }

        fun buildNode(node: Category): CategoryNodeResponse =
            CategoryNodeResponse(
                id = requireNotNull(node.id),
                name = node.name,
                children =
                    (childrenByParentId[node.id] ?: emptyList())
                        .sortedBy { it.name }
                        .map { buildNode(it) },
            )

        return (childrenByParentId[null] ?: emptyList())
            .sortedBy { it.name }
            .map { buildNode(it) }
    }
}
