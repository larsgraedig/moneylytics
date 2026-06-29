package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface BudgetJpaRepository : JpaRepository<BudgetEntity, Long> {
    fun findByUserId(userId: Long): List<BudgetEntity>

    @Modifying
    @Query("DELETE FROM BudgetEntity b WHERE b.id = :id AND b.user.id = :userId")
    fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    )
}
