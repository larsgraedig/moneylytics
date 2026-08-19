package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Tier

interface GetUserTierUseCase {
    fun getUserTier(userId: Long): Tier
}
