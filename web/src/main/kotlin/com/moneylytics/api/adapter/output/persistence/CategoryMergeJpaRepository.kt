package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CategoryMergeJpaRepository : JpaRepository<CategoryMergeEntity, Long> {
    @Query(
        "SELECT m FROM CategoryMergeEntity m WHERE m.organization.id = :organizationId AND m.revertedAt IS NULL",
    )
    fun findActiveByOrganizationId(
        @Param("organizationId") organizationId: Long,
    ): List<CategoryMergeEntity>

    @Query(
        "SELECT m FROM CategoryMergeEntity m WHERE m.id = :id AND m.organization.id = :organizationId",
    )
    fun findByIdAndOrganizationId(
        @Param("id") id: Long,
        @Param("organizationId") organizationId: Long,
    ): CategoryMergeEntity?

    @Query(
        "SELECT t.transactionId FROM CategoryMergeTransactionEntity t WHERE t.mergeId = :mergeId",
    )
    fun findTransactionIdsByMergeId(
        @Param("mergeId") mergeId: Long,
    ): List<Long>

    @Query(
        "SELECT c.childCategoryId FROM CategoryMergeChildEntity c WHERE c.mergeId = :mergeId",
    )
    fun findChildCategoryIdsByMergeId(
        @Param("mergeId") mergeId: Long,
    ): List<Long>

    @Modifying
    @Query(
        "UPDATE CategoryMergeEntity m SET m.revertedAt = :now WHERE m.id = :mergeId",
    )
    fun markReverted(
        @Param("mergeId") mergeId: Long,
        @Param("now") now: Instant,
    )
}
