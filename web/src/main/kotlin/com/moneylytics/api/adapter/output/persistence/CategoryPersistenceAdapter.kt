package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.domain.Category
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CategoryPersistenceAdapter(
    private val jpaRepository: CategoryJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
) : CategoryRepository {
    @Transactional(readOnly = true)
    override fun findAll(organizationId: Long): List<Category> =
        jpaRepository.findAllByOrganizationId(organizationId).map {
            Category(name = it.name, subcategory = it.subcategory, group = it.categoryGroup)
        }

    @Transactional
    override fun saveAllIfAbsent(
        categories: List<Category>,
        organizationId: Long,
    ) {
        val existing =
            jpaRepository
                .findAllByOrganizationId(organizationId)
                .map { Triple(it.name, it.categoryGroup, it.subcategory) }
                .toHashSet()
        val organization = organizationJpaRepository.getReferenceById(organizationId)
        val toSave =
            categories
                .filter { Triple(it.name, it.group, it.subcategory) !in existing }
                .map {
                    CategoryEntity(name = it.name, subcategory = it.subcategory, organization = organization, categoryGroup = it.group)
                }
        jpaRepository.saveAll(toSave)
    }
}
