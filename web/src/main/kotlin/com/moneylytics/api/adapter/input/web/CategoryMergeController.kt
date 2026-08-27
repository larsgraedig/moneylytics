package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.GetCategoryMergesUseCase
import com.moneylytics.api.application.port.input.MergeCategoriesCommand
import com.moneylytics.api.application.port.input.MergeCategoriesUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.RevertMergeCommand
import com.moneylytics.api.application.port.input.RevertMergeUseCase
import com.moneylytics.api.domain.CategoryMerge
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.time.Instant

private val logger = KotlinLogging.logger {}

data class MergeCategoriesRequest(
    val sourceId: Long,
    val targetId: Long,
)

data class CategoryMergeResponse(
    val id: Long,
    val sourceCategoryId: Long?,
    val sourceName: String,
    val targetCategoryId: Long,
    val targetName: String,
    val mergedAt: Instant,
    val transactionCount: Int,
    val childCount: Int,
)

data class MergeErrorResponse(
    val reason: String,
)

@RestController
@RequestMapping("/categories/merges")
class CategoryMergeController(
    private val mergeCategoriesUseCase: MergeCategoriesUseCase,
    private val revertMergeUseCase: RevertMergeUseCase,
    private val getCategoryMergesUseCase: GetCategoryMergesUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    @PostMapping
    suspend fun mergeCategories(
        @RequestBody request: MergeCategoriesRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Any> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return try {
            val (merge, nameById) =
                withContext(Dispatchers.IO) {
                    val m =
                        mergeCategoriesUseCase.mergeCategories(
                            MergeCategoriesCommand(
                                sourceId = request.sourceId,
                                targetId = request.targetId,
                                organizationId = organizationId,
                            ),
                        )
                    val names = getCategoriesUseCase.getCategories(organizationId).associate { it.id to it.name }
                    m to names
                }
            ResponseEntity.status(HttpStatus.CREATED).body(merge.toResponse(nameById))
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(MergeErrorResponse(reason = e.message ?: "UNKNOWN"))
        } catch (e: DataIntegrityViolationException) {
            logger.warn(e) { "DataIntegrityViolation in category merge operation" }
            ResponseEntity.status(HttpStatus.CONFLICT).body(MergeErrorResponse(reason = "CONSTRAINT_VIOLATION"))
        }
    }

    @GetMapping
    suspend fun getMerges(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): List<CategoryMergeResponse> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            val nameById = getCategoriesUseCase.getCategories(organizationId).associate { it.id to it.name }
            getCategoryMergesUseCase.getMerges(organizationId).map { it.toResponse(nameById) }
        }
    }

    @DeleteMapping("/{id}")
    suspend fun revertMerge(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Any> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return try {
            withContext(Dispatchers.IO) {
                revertMergeUseCase.revertMerge(RevertMergeCommand(mergeId = id, organizationId = organizationId))
            }
            ResponseEntity.noContent().build()
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(MergeErrorResponse(reason = e.message ?: "UNKNOWN"))
        } catch (e: DataIntegrityViolationException) {
            logger.warn(e) { "DataIntegrityViolation in category merge operation" }
            ResponseEntity.status(HttpStatus.CONFLICT).body(MergeErrorResponse(reason = "CONSTRAINT_VIOLATION"))
        }
    }

    private fun CategoryMerge.toResponse(nameById: Map<Long?, String> = emptyMap()) =
        CategoryMergeResponse(
            id = id,
            sourceCategoryId = sourceCategoryId,
            sourceName = sourceName,
            targetCategoryId = targetCategoryId,
            targetName = nameById[targetCategoryId] ?: "",
            mergedAt = mergedAt,
            transactionCount = transactionCount,
            childCount = childCount,
        )
}
