package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Tier

interface TierRepository {
    fun findAll(): List<Tier>

    fun findById(id: Long): Tier?

    fun findDefault(): Tier

    fun save(tier: Tier): Tier

    fun setDefault(tierId: Long)

    fun findByStripePriceId(priceId: String): Tier?

    fun setStripePrice(
        tierId: Long,
        priceId: String?,
    )

    fun findByUserId(userId: Long): Tier

    fun assignToUser(
        userId: Long,
        tierId: Long,
    )
}
