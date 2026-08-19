package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Tier

interface CreateTierUseCase {
    fun createTier(
        name: String,
        description: String?,
        isDefault: Boolean,
    ): Tier
}
