package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.FindOrCreateCategoryUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.SaveCategoriesUseCase
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.domain.Category
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
) : GetCategoriesUseCase,
    SaveCategoriesUseCase,
    FindOrCreateCategoryUseCase {
    override fun getCategories(organizationId: Long): List<Category> = categoryRepository.findAll(organizationId)

    override fun saveCategories(
        categories: List<Category>,
        organizationId: Long,
    ) = categoryRepository.saveAllIfAbsent(categories, organizationId)

    override fun findOrCreateCategory(
        name: String,
        subcategory: String?,
        group: String,
        organizationId: Long,
    ): Category = categoryRepository.findOrCreate(name, subcategory, group, organizationId)
}
