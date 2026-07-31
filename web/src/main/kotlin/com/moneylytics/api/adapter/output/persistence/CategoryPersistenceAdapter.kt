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
    override fun findAll(organizationId: Long): List<Category> = jpaRepository.findAllByOrganizationId(organizationId).map { it.toDomain() }

    @Transactional
    override fun saveAllIfAbsent(
        categories: List<Category>,
        organizationId: Long,
    ) {
        val existing =
            jpaRepository
                .findAllByOrganizationId(organizationId)
                .map { Triple(it.name, it.subcategory, it.groupName) }
                .toHashSet()
        val organization = organizationJpaRepository.getReferenceById(organizationId)
        val toSave =
            categories
                .filter { Triple(it.name, it.subcategory, it.group) !in existing }
                .map { CategoryEntity(name = it.name, subcategory = it.subcategory, groupName = it.group, organization = organization) }
        jpaRepository.saveAll(toSave)
    }

    @Transactional
    override fun findOrCreate(
        name: String,
        subcategory: String,
        group: String?,
        organizationId: Long,
    ): Category {
        val existing = jpaRepository.findByNameAndSubcategoryAndGroupNameAndOrganizationId(name, subcategory, group, organizationId)
        if (existing != null) return existing.toDomain()
        val organization = organizationJpaRepository.getReferenceById(organizationId)
        return jpaRepository
            .save(
                CategoryEntity(name = name, subcategory = subcategory, groupName = group, organization = organization),
            ).toDomain()
    }

    private fun CategoryEntity.toDomain() = Category(name = name, subcategory = subcategory, group = groupName, id = id)
}
