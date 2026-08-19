package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Tier

interface TierRepository {
    fun findAll(): List<Tier>

    fun findById(id: Long): Tier?

    fun findDefault(): Tier

    fun save(tier: Tier): Tier

    fun setDefault(tierId: Long)

    fun findByUserId(userId: Long): Tier

    fun assignToUser(
        userId: Long,
        tierId: Long,
    )
}
