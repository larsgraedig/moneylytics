package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetCategoryMergesUseCase
import com.moneylytics.api.application.port.input.MergeCategoriesCommand
import com.moneylytics.api.application.port.input.MergeCategoriesUseCase
import com.moneylytics.api.application.port.input.RevertMergeCommand
import com.moneylytics.api.application.port.input.RevertMergeUseCase
import com.moneylytics.api.application.port.output.CategoryMergeRepository
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.CategoryMerge
import com.moneylytics.api.domain.NewCategoryMerge
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CategoryMergeService(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val thresholdRepository: ThresholdRepository,
    private val categoryMergeRepository: CategoryMergeRepository,
) : MergeCategoriesUseCase,
    RevertMergeUseCase,
    GetCategoryMergesUseCase {
    @Transactional
    override fun mergeCategories(command: MergeCategoriesCommand): CategoryMerge {
        require(command.sourceId != command.targetId) { "SAME_CATEGORY" }

        val allCategories = categoryRepository.findAll(command.organizationId)
        val source =
            allCategories.firstOrNull { it.id == command.sourceId }
                ?: throw NoSuchElementException("Category ${command.sourceId} not found")

        val txIds = transactionRepository.findIdsByCategoryId(command.sourceId, command.organizationId)
        val childIds = categoryRepository.findChildIds(command.sourceId, command.organizationId)

        transactionRepository.moveToCategoryBySource(command.sourceId, command.targetId, command.organizationId)
        categoryRepository.reparentChildren(command.sourceId, command.targetId, command.organizationId)

        val hasThresholds = thresholdRepository.existsByCategoryId(command.sourceId, command.organizationId)

        val merge =
            categoryMergeRepository.save(
                NewCategoryMerge(
                    sourceCategoryId = if (hasThresholds) command.sourceId else null,
                    sourceName = source.name,
                    sourceParentId = source.parentId,
                    targetCategoryId = command.targetId,
                    organizationId = command.organizationId,
                    transactionIds = txIds,
                    childCategoryIds = childIds,
                ),
            )

        if (!hasThresholds) {
            categoryRepository.delete(command.sourceId, command.organizationId)
        }

        return merge
    }

    @Transactional
    override fun revertMerge(command: RevertMergeCommand) {
        val merge =
            categoryMergeRepository.findById(command.mergeId, command.organizationId)
                ?: throw NoSuchElementException("Merge ${command.mergeId} not found")
        check(!merge.reverted) { "ALREADY_REVERTED" }

        val sourceId: Long =
            if (merge.sourceCategoryId != null) {
                merge.sourceCategoryId
            } else {
                val path = buildRestorePath(merge, command.organizationId)
                requireNotNull(categoryRepository.findOrCreate(path, command.organizationId).id)
            }

        val txIds = categoryMergeRepository.getTransactionIds(command.mergeId)
        if (txIds.isNotEmpty()) {
            transactionRepository.moveToCategoryBulk(txIds, sourceId, command.organizationId)
        }

        val childIds = categoryMergeRepository.getChildCategoryIds(command.mergeId)
        for (childId in childIds) {
            categoryRepository.move(childId, sourceId, command.organizationId)
        }

        categoryMergeRepository.markReverted(command.mergeId)
    }

    override fun getMerges(organizationId: Long): List<CategoryMerge> = categoryMergeRepository.findActiveByOrganization(organizationId)

    private fun buildRestorePath(
        merge: CategoryMerge,
        organizationId: Long,
    ): List<String> {
        val parentId = merge.sourceParentId ?: return listOf(merge.sourceName)

        val allCategories = categoryRepository.findAll(organizationId)
        val byId = allCategories.associateBy { it.id }

        val path = mutableListOf<String>()
        var current = byId[parentId]
        while (current != null) {
            path.add(0, current.name)
            current = current.parentId?.let { byId[it] }
        }

        if (path.isEmpty()) {
            throw IllegalStateException("PARENT_NOT_FOUND")
        }

        path.add(merge.sourceName)
        return path
    }
}
