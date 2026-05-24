package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.SaveCategoriesUseCase
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.domain.Category
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
) : GetCategoriesUseCase,
    SaveCategoriesUseCase {
    override fun getCategories(): List<Category> = categoryRepository.findAll()

    override fun saveCategories(categories: List<Category>) = categoryRepository.saveAllIfAbsent(categories)
}
