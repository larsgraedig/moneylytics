package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ThresholdPersistenceAdapter(
    private val jpaRepository: ThresholdJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
) : ThresholdRepository {
    override fun findAllByOrganizationId(organizationId: Long): List<Threshold> =
        jpaRepository.findByOrganizationId(organizationId).map { it.toDomain() }

    @Transactional
    override fun upsert(
        threshold: Threshold,
        organizationId: Long,
    ): Threshold {
        val existing = findExisting(organizationId, threshold.category, threshold.subcategory, threshold.period)
        return if (existing != null) {
            existing.notice = threshold.notice
            existing.warning = threshold.warning
            existing.critical = threshold.critical
            jpaRepository.save(existing).toDomain()
        } else {
            jpaRepository
                .save(
                    ThresholdEntity(
                        organization = organizationJpaRepository.getReferenceById(organizationId),
                        category = threshold.category,
                        subcategory = threshold.subcategory,
                        groupName = threshold.group,
                        period = threshold.period,
                        notice = threshold.notice,
                        warning = threshold.warning,
                        critical = threshold.critical,
                    ),
                ).toDomain()
        }
    }

    @Transactional
    override fun deleteByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ) = jpaRepository.deleteByIdAndOrganizationId(id, organizationId)

    private fun findExisting(
        organizationId: Long,
        category: String,
        subcategory: String?,
        period: ThresholdPeriod,
    ): ThresholdEntity? =
        if (subcategory == null) {
            jpaRepository.findByOrganizationIdAndCategoryAndSubcategoryIsNullAndPeriod(organizationId, category, period)
        } else {
            jpaRepository.findByOrganizationIdAndCategoryAndSubcategoryAndPeriod(organizationId, category, subcategory, period)
        }

    private fun ThresholdEntity.toDomain() =
        Threshold(
            id = id!!,
            category = category,
            subcategory = subcategory,
            group = groupName,
            period = period,
            notice = notice,
            warning = warning,
            critical = critical,
        )
}
