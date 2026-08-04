package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.CategoryStatItem
import com.moneylytics.api.application.port.input.DeleteCategoryUseCase
import com.moneylytics.api.application.port.input.FindOrCreateCategoryUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.GetCategoryStatsQuery
import com.moneylytics.api.application.port.input.GetCategoryStatsUseCase
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Category
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) : GetCategoriesUseCase,
    FindOrCreateCategoryUseCase,
    GetCategoryStatsUseCase,
    DeleteCategoryUseCase {
    override fun getCategories(organizationId: Long): List<Category> = categoryRepository.findAll(organizationId)

    override fun findOrCreateCategory(
        path: List<String>,
        organizationId: Long,
    ): Category = categoryRepository.findOrCreate(path, organizationId)

    override fun getCategoryStats(query: GetCategoryStatsQuery): List<CategoryStatItem> {
        val total = transactionRepository.countByCategoryGrouped(query.organizationId, query.iban)
        val period =
            transactionRepository.countByCategoryGroupedInPeriod(
                query.organizationId,
                query.from,
                query.to,
                query.iban,
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
    ) = categoryRepository.delete(id, organizationId)
}
