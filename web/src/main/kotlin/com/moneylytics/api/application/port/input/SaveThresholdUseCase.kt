package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Threshold

interface SaveThresholdUseCase {
    fun saveThreshold(
        threshold: Threshold,
        organizationId: Long,
    ): Threshold
}
