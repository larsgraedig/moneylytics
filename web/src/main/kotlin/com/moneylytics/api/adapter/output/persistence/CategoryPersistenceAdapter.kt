package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.domain.Category
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CategoryPersistenceAdapter(
    private val jpaRepository: CategoryJpaRepository,
) : CategoryRepository {
    @Transactional(readOnly = true)
    override fun findAll(): List<Category> = jpaRepository.findAll().map { Category(name = it.name, subcategory = it.subcategory) }

    @Transactional
    override fun saveAllIfAbsent(categories: List<Category>) {
        val existing =
            jpaRepository
                .findAll()
                .map { it.name to it.subcategory }
                .toHashSet()
        val toSave =
            categories
                .filter { (it.name to it.subcategory) !in existing }
                .map { CategoryEntity(name = it.name, subcategory = it.subcategory) }
        jpaRepository.saveAll(toSave)
    }
}
