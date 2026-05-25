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
        jpaRepository.findAllByUserId(userId).map { Category(name = it.name, subcategory = it.subcategory) }

    @Transactional
    override fun saveAllIfAbsent(
        categories: List<Category>,
        userId: Long,
    ) {
        val existing =
            jpaRepository
                .findAllByUserId(userId)
                .map { it.name to it.subcategory }
                .toHashSet()
        val user = userJpaRepository.getReferenceById(userId)
        val toSave =
            categories
                .filter { (it.name to it.subcategory) !in existing }
                .map { CategoryEntity(name = it.name, subcategory = it.subcategory, user = user) }
        jpaRepository.saveAll(toSave)
    }
}
