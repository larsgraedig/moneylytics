package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Threshold

interface ThresholdRepository {
    fun findAllByUserId(userId: Long): List<Threshold>

    fun upsert(
        threshold: Threshold,
        userId: Long,
    ): Threshold

    fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    )
}
