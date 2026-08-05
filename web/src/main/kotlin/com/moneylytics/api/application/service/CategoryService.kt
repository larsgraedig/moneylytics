package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.CategoryStatItem
import com.moneylytics.api.application.port.input.DeleteCategoryUseCase
import com.moneylytics.api.application.port.input.FindOrCreateCategoryUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.GetCategoryStatsQuery
import com.moneylytics.api.application.port.input.GetCategoryStatsUseCase
import com.moneylytics.api.application.port.input.MoveCategoryCommand
import com.moneylytics.api.application.port.input.MoveCategoryUseCase
import com.moneylytics.api.application.port.input.RenameCategoryCommand
import com.moneylytics.api.application.port.input.RenameCategoryUseCase
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Category
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val thresholdRepository: ThresholdRepository,
) : GetCategoriesUseCase,
    FindOrCreateCategoryUseCase,
    GetCategoryStatsUseCase,
    DeleteCategoryUseCase,
    MoveCategoryUseCase,
    RenameCategoryUseCase {
    override fun getCategories(organizationId: Long): List<Category> = categoryRepository.findAll(organizationId)

    override fun findOrCreateCategory(
        path: List<String>,
        organizationId: Long,
    ): Category = categoryRepository.findOrCreate(path, organizationId)

    override fun getCategoryStats(query: GetCategoryStatsQuery): List<CategoryStatItem> {
        val total = transactionRepository.countByCategoryGrouped(query.organizationId, query.accountId)
        val period =
            transactionRepository.countByCategoryGroupedInPeriod(
                query.organizationId,
                query.from,
                query.to,
                query.accountId,
            )
        val allIds = total.keys + period.keys
        return allIds.map { id ->
            CategoryStatItem(
                categoryId = id,
                totalCount = total[id] ?: 0L,
                periodCount = period[id] ?: 0L,
            )
        }
    }

    override fun deleteCategory(
        id: Long,
        organizationId: Long,
    ) {
        if (thresholdRepository.existsByCategoryId(id, organizationId)) {
            throw IllegalStateException("THRESHOLD_EXISTS")
        }
        categoryRepository.delete(id, organizationId)
    }

    override fun moveCategory(command: MoveCategoryCommand) {
        val newParentId = command.newParentId
        if (newParentId != null) {
            val allCategories = categoryRepository.findAll(command.organizationId)
            val descendants = collectDescendants(command.id, allCategories)
            if (newParentId == command.id || newParentId in descendants) {
                throw IllegalArgumentException("CIRCULAR_REFERENCE")
            }
        }
        categoryRepository.move(command.id, command.newParentId, command.organizationId)
    }

    override fun renameCategory(command: RenameCategoryCommand) {
        require(command.newName.isNotBlank()) { "EMPTY_NAME" }
        categoryRepository.rename(command.id, command.newName.trim(), command.organizationId)
    }

    private fun collectDescendants(
        id: Long,
        all: List<Category>,
    ): Set<Long> {
        val childrenByParentId = all.groupBy { it.parentId }
        val result = mutableSetOf<Long>()
        val queue = ArrayDeque(childrenByParentId[id]?.mapNotNull { it.id } ?: emptyList())
        while (queue.isNotEmpty()) {
            val childId = queue.removeFirst()
            result.add(childId)
            childrenByParentId[childId]?.mapNotNull { it.id }?.let { queue.addAll(it) }
        }
        return result
    }
}
