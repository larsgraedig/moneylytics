package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.DeleteCategoryUseCase
import com.moneylytics.api.application.port.input.FindOrCreateCategoryUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.GetCategoryStatsQuery
import com.moneylytics.api.application.port.input.GetCategoryStatsUseCase
import com.moneylytics.api.application.port.input.MoveCategoryCommand
import com.moneylytics.api.application.port.input.MoveCategoryUseCase
import com.moneylytics.api.application.port.input.RenameCategoryCommand
import com.moneylytics.api.application.port.input.RenameCategoryUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.domain.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.time.LocalDate

data class CreateCategoryRequest(
    val path: List<String>,
)

data class MoveCategoryRequest(
    val newParentId: Long?,
)

data class RenameCategoryRequest(
    val name: String,
)

@RestController
@RequestMapping("/categories")
class CategoryController(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val findOrCreateCategoryUseCase: FindOrCreateCategoryUseCase,
    private val getCategoryStatsUseCase: GetCategoryStatsUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val moveCategoryUseCase: MoveCategoryUseCase,
    private val renameCategoryUseCase: RenameCategoryUseCase,
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

    @GetMapping("/stats")
    suspend fun getCategoryStats(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
        @RequestParam(required = false) accountId: Long? = null,
    ): CategoryStatsResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val items =
            withContext(Dispatchers.IO) {
                getCategoryStatsUseCase.getCategoryStats(
                    GetCategoryStatsQuery(
                        organizationId = organizationId,
                        from = from,
                        to = to,
                        accountId = accountId,
                    ),
                )
            }
        return CategoryStatsResponse(
            items = items.map { CategoryStatItemResponse(it.categoryId, it.totalCount, it.periodCount) },
        )
    }

    @PatchMapping("/{id}/parent")
    suspend fun moveCategory(
        @PathVariable id: Long,
        @RequestBody request: MoveCategoryRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Any> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return try {
            withContext(Dispatchers.IO) {
                moveCategoryUseCase.moveCategory(
                    MoveCategoryCommand(
                        id = id,
                        newParentId = request.newParentId,
                        organizationId = organizationId,
                    ),
                )
            }
            ResponseEntity.noContent().build()
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(DeleteCategoryErrorResponse(reason = e.message ?: "UNKNOWN"))
        } catch (_: DataIntegrityViolationException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(DeleteCategoryErrorResponse(reason = "CONSTRAINT_VIOLATION"))
        }
    }

    @PatchMapping("/{id}/name")
    suspend fun renameCategory(
        @PathVariable id: Long,
        @RequestBody request: RenameCategoryRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Any> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return try {
            withContext(Dispatchers.IO) {
                renameCategoryUseCase.renameCategory(
                    RenameCategoryCommand(
                        id = id,
                        newName = request.name,
                        organizationId = organizationId,
                    ),
                )
            }
            ResponseEntity.noContent().build()
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(DeleteCategoryErrorResponse(reason = e.message ?: "UNKNOWN"))
        } catch (_: DataIntegrityViolationException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(DeleteCategoryErrorResponse(reason = "CONSTRAINT_VIOLATION"))
        }
    }

    @DeleteMapping("/{id}")
    suspend fun deleteCategory(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Any> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return try {
            withContext(Dispatchers.IO) { deleteCategoryUseCase.deleteCategory(id, organizationId) }
            ResponseEntity.noContent().build()
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(DeleteCategoryErrorResponse(reason = e.message ?: "UNKNOWN"))
        } catch (_: DataIntegrityViolationException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(DeleteCategoryErrorResponse(reason = "CONSTRAINT_VIOLATION"))
        }
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
