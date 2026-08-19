package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface TierJpaRepository : JpaRepository<TierEntity, Long> {
    fun findByIsDefaultTrue(): TierEntity?

    fun findByStripePriceId(stripePriceId: String): TierEntity?

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TierEntity t SET t.isDefault = false WHERE t.isDefault = true")
    fun clearDefault()
}
