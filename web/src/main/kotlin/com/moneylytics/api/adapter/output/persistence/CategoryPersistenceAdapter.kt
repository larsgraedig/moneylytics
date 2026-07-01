package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.domain.Category
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CategoryPersistenceAdapter(
    private val jpaRepository: CategoryJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : CategoryRepository {
    @Transactional(readOnly = true)
    override fun findAll(userId: Long): List<Category> =
        jpaRepository.findAllByUserId(userId).map { Category(name = it.name, subcategory = it.subcategory, group = it.categoryGroup) }

    @Transactional
    override fun saveAllIfAbsent(
        categories: List<Category>,
        userId: Long,
    ) {
        val existing =
            jpaRepository
                .findAllByUserId(userId)
                .map { Triple(it.name, it.categoryGroup, it.subcategory) }
                .toHashSet()
        val user = userJpaRepository.getReferenceById(userId)
        val toSave =
            categories
                .filter { Triple(it.name, it.group, it.subcategory) !in existing }
                .map { CategoryEntity(name = it.name, subcategory = it.subcategory, user = user, categoryGroup = it.group) }
        jpaRepository.saveAll(toSave)
    }
}
