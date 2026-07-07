package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CollectionJpaRepository : JpaRepository<CollectionEntity, Long> {
    fun findByUserId(userId: Long): List<CollectionEntity>

    @Modifying
    @Query("DELETE FROM CollectionEntity c WHERE c.id = :id AND c.user.id = :userId")
    fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    )
}
