package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CategoryMergeRepository
import com.moneylytics.api.domain.CategoryMerge
import com.moneylytics.api.domain.NewCategoryMerge
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class CategoryMergePersistenceAdapter(
    private val mergeJpaRepository: CategoryMergeJpaRepository,
    private val transactionJpaRepository: CategoryMergeTransactionJpaRepository,
    private val childJpaRepository: CategoryMergeChildJpaRepository,
    private val categoryJpaRepository: CategoryJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
) : CategoryMergeRepository {
    @Transactional
    override fun save(merge: NewCategoryMerge): CategoryMerge {
        val entity =
            CategoryMergeEntity(
                sourceCategoryId = merge.sourceCategoryId,
                sourceName = merge.sourceName,
                sourceParentId = merge.sourceParentId,
                targetCategory = categoryJpaRepository.getReferenceById(merge.targetCategoryId),
                organization = organizationJpaRepository.getReferenceById(merge.organizationId),
            )
        val saved = mergeJpaRepository.save(entity)
        val mergeId = requireNotNull(saved.id)

        transactionJpaRepository.saveAll(
            merge.transactionIds.map { CategoryMergeTransactionEntity(mergeId, it) },
        )
        childJpaRepository.saveAll(
            merge.childCategoryIds.map { CategoryMergeChildEntity(mergeId, it) },
        )

        return saved.toDomain(merge.transactionIds.size, merge.childCategoryIds.size)
    }

    @Transactional(readOnly = true)
    override fun findActiveByOrganization(organizationId: Long): List<CategoryMerge> =
        mergeJpaRepository.findActiveByOrganizationId(organizationId).map { entity ->
            val txCount = mergeJpaRepository.findTransactionIdsByMergeId(requireNotNull(entity.id)).size
            val childCount = mergeJpaRepository.findChildCategoryIdsByMergeId(requireNotNull(entity.id)).size
            entity.toDomain(txCount, childCount)
        }

    @Transactional(readOnly = true)
    override fun findById(
        mergeId: Long,
        organizationId: Long,
    ): CategoryMerge? {
        val entity = mergeJpaRepository.findByIdAndOrganizationId(mergeId, organizationId) ?: return null
        val txCount = mergeJpaRepository.findTransactionIdsByMergeId(mergeId).size
        val childCount = mergeJpaRepository.findChildCategoryIdsByMergeId(mergeId).size
        return entity.toDomain(txCount, childCount)
    }

    @Transactional(readOnly = true)
    override fun getTransactionIds(mergeId: Long): List<Long> = mergeJpaRepository.findTransactionIdsByMergeId(mergeId)

    @Transactional(readOnly = true)
    override fun getChildCategoryIds(mergeId: Long): List<Long> = mergeJpaRepository.findChildCategoryIdsByMergeId(mergeId)

    @Transactional
    override fun markReverted(mergeId: Long) {
        mergeJpaRepository.markReverted(mergeId, Instant.now())
    }

    private fun CategoryMergeEntity.toDomain(
        txCount: Int,
        childCount: Int,
    ) = CategoryMerge(
        id = requireNotNull(id),
        sourceCategoryId = sourceCategoryId,
        sourceName = sourceName,
        sourceParentId = sourceParentId,
        targetCategoryId = targetCategory.id ?: error("target category has no id"),
        mergedAt = mergedAt,
        transactionCount = txCount,
        childCount = childCount,
        reverted = revertedAt != null,
    )
}
