package com.moneylytics.api.application.port.input

interface DeleteThresholdUseCase {
    fun deleteThreshold(
        id: Long,
        organizationId: Long,
    )
}
