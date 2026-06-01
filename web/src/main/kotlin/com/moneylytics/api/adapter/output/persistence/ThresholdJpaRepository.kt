package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.ThresholdPeriod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ThresholdJpaRepository : JpaRepository<ThresholdEntity, Long> {
    fun findByUserId(userId: Long): List<ThresholdEntity>

    fun findByUserIdAndCategoryAndSubcategoryIsNullAndPeriod(
        userId: Long,
        category: String,
        period: ThresholdPeriod,
    ): ThresholdEntity?

    fun findByUserIdAndCategoryAndSubcategoryAndPeriod(
        userId: Long,
        category: String,
        subcategory: String,
        period: ThresholdPeriod,
    ): ThresholdEntity?

    @Modifying
    @Query("DELETE FROM ThresholdEntity t WHERE t.id = :id AND t.user.id = :userId")
    fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    )
}
