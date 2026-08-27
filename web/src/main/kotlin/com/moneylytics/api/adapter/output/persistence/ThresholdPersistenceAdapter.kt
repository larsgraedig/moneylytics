package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.domain.Threshold
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ThresholdPersistenceAdapter(
    private val jpaRepository: ThresholdJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
    private val categoryJpaRepository: CategoryJpaRepository,
) : ThresholdRepository {
    override fun findAllByOrganizationId(organizationId: Long): List<Threshold> =
        jpaRepository
            .findByOrganizationIdAndCategoryEntityIsNotNull(organizationId)
            .mapNotNull { it.toDomain() }

    @Transactional
    override fun upsert(
        threshold: Threshold,
        organizationId: Long,
    ): Threshold {
        val existing =
            jpaRepository.findByOrganizationIdAndCategoryEntityIdAndPeriod(
                organizationId,
                threshold.categoryId,
                threshold.period,
            )
        return if (existing != null) {
            existing.notice = threshold.notice
            existing.warning = threshold.warning
            existing.critical = threshold.critical
            requireNotNull(jpaRepository.save(existing).toDomain())
        } else {
            val categoryEntity = categoryJpaRepository.getReferenceById(threshold.categoryId)
            jpaRepository
                .save(
                    ThresholdEntity(
                        organization = organizationJpaRepository.getReferenceById(organizationId),
                        categoryEntity = categoryEntity,
                        period = threshold.period,
                        notice = threshold.notice,
                        warning = threshold.warning,
                        critical = threshold.critical,
                    ),
                ).let { requireNotNull(it.toDomain()) }
        }
    }

    @Transactional
    override fun deleteByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ) = jpaRepository.deleteByIdAndOrganizationId(id, organizationId)

    override fun existsByCategoryId(
        categoryId: Long,
        organizationId: Long,
    ): Boolean = jpaRepository.existsByOrganizationIdAndCategoryEntityId(organizationId, categoryId)

    private fun ThresholdEntity.toDomain(): Threshold? {
        val cat = categoryEntity ?: return null
        return Threshold(
            id = requireNotNull(id),
            categoryId = requireNotNull(cat.id),
            categoryPath = cat.buildPath(),
            period = period,
            notice = notice,
            warning = warning,
            critical = critical,
        )
    }
}
