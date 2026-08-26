package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TransactionImportJpaRepository : JpaRepository<TransactionImportEntity, Long> {
    fun findByOrganizationIdOrderByImportedAtDesc(organizationId: Long): List<TransactionImportEntity>

    @Query("SELECT ti FROM TransactionImportEntity ti WHERE ti.id = :id AND ti.organization.id = :orgId")
    fun findByIdAndOrganizationId(
        @Param("id") id: Long,
        @Param("orgId") orgId: Long,
    ): TransactionImportEntity?
}
