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
    override fun findOrCreate(
        path: List<String>,
        organizationId: Long,
    ): Category {
        require(path.isNotEmpty()) { "Category path must not be empty" }
        val organization = organizationJpaRepository.getReferenceById(organizationId)
        var parentId: Long? = null
        var current: CategoryEntity? = null
        for (name in path) {
            current = jpaRepository.findByNameAndParentIdAndOrganizationId(name, parentId, organizationId)
                ?: jpaRepository.save(
                    CategoryEntity(
                        name = name,
                        parent = parentId?.let { jpaRepository.getReferenceById(it) },
                        organization = organization,
                    ),
                )
            parentId = requireNotNull(current.id)
        }
        return requireNotNull(current).toDomain()
    }

    @Transactional
    override fun delete(
        id: Long,
        organizationId: Long,
    ) {
        val entity =
            jpaRepository.findByIdAndOrganizationId(id, organizationId)
                ?: throw NoSuchElementException("Category $id not found")
        jpaRepository.delete(entity)
    }

    @Transactional
    override fun move(
        id: Long,
        newParentId: Long?,
        organizationId: Long,
    ) {
        val entity =
            jpaRepository.findByIdAndOrganizationId(id, organizationId)
                ?: throw NoSuchElementException("Category $id not found")
        entity.parent = newParentId?.let { jpaRepository.getReferenceById(it) }
        jpaRepository.save(entity)
    }

    @Transactional
    override fun rename(
        id: Long,
        newName: String,
        organizationId: Long,
    ) {
        val entity =
            jpaRepository.findByIdAndOrganizationId(id, organizationId)
                ?: throw NoSuchElementException("Category $id not found")
        entity.name = newName
        jpaRepository.save(entity)
    }

    private fun CategoryEntity.toDomain() = Category(id = id, name = name, parentId = parent?.id)
}
