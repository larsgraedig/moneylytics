package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.FindOrCreateCategoryUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.domain.Category
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
) : GetCategoriesUseCase,
    FindOrCreateCategoryUseCase {
    override fun getCategories(organizationId: Long): List<Category> = categoryRepository.findAll(organizationId)

    override fun findOrCreateCategory(
        path: List<String>,
        organizationId: Long,
    ): Category = categoryRepository.findOrCreate(path, organizationId)
}
