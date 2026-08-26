package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CustomerTierJpaRepository : JpaRepository<CustomerTierEntity, Long> {
    fun findByIsDefaultTrue(): CustomerTierEntity?

    fun findByStripePriceId(stripePriceId: String): CustomerTierEntity?

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CustomerTierEntity t SET t.isDefault = false WHERE t.isDefault = true")
    fun clearDefault()
}
