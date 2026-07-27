package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CollectionJpaRepository : JpaRepository<CollectionEntity, Long> {
    fun findByOrganizationId(organizationId: Long): List<CollectionEntity>

    fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): CollectionEntity?

    @Modifying
    @Query("DELETE FROM CollectionEntity c WHERE c.id = :id AND c.organization.id = :organizationId")
    fun deleteByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    )
}
