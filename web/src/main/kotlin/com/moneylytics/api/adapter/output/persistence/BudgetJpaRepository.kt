package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BudgetJpaRepository : JpaRepository<BudgetEntity, Long> {
    fun findByOrganizationId(organizationId: Long): List<BudgetEntity>

    @Modifying
    @Query("DELETE FROM BudgetEntity b WHERE b.id = :id AND b.organization.id = :organizationId")
    fun deleteByIdAndOrganizationId(
        @Param("id") id: Long,
        @Param("organizationId") organizationId: Long,
    )
}
