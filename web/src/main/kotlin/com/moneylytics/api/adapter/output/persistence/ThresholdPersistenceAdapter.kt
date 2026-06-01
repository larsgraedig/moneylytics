package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ThresholdPersistenceAdapter(
    private val jpaRepository: ThresholdJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : ThresholdRepository {
    override fun findAllByUserId(userId: Long): List<Threshold> = jpaRepository.findByUserId(userId).map { it.toDomain() }

    @Transactional
    override fun upsert(
        threshold: Threshold,
        userId: Long,
    ): Threshold {
        val existing = findExisting(userId, threshold.category, threshold.subcategory, threshold.period)
        return if (existing != null) {
            existing.notice = threshold.notice
            existing.warning = threshold.warning
            existing.critical = threshold.critical
            jpaRepository.save(existing).toDomain()
        } else {
            jpaRepository
                .save(
                    ThresholdEntity(
                        user = userJpaRepository.getReferenceById(userId),
                        category = threshold.category,
                        subcategory = threshold.subcategory,
                        period = threshold.period,
                        notice = threshold.notice,
                        warning = threshold.warning,
                        critical = threshold.critical,
                    ),
                ).toDomain()
        }
    }

    @Transactional
    override fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    ) = jpaRepository.deleteByIdAndUserId(id, userId)

    private fun findExisting(
        userId: Long,
        category: String,
        subcategory: String?,
        period: ThresholdPeriod,
    ): ThresholdEntity? =
        if (subcategory == null) {
            jpaRepository.findByUserIdAndCategoryAndSubcategoryIsNullAndPeriod(userId, category, period)
        } else {
            jpaRepository.findByUserIdAndCategoryAndSubcategoryAndPeriod(userId, category, subcategory, period)
        }

    private fun ThresholdEntity.toDomain() =
        Threshold(
            id = id!!,
            category = category,
            subcategory = subcategory,
            period = period,
            notice = notice,
            warning = warning,
            critical = critical,
        )
}
