package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TransactionGroupJpaRepository : JpaRepository<TransactionGroupEntity, Long> {
    fun findAllByOrganizationId(organizationId: Long): List<TransactionGroupEntity>

    fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): TransactionGroupEntity?
}
