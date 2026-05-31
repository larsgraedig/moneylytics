package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.DeleteThresholdUseCase
import com.moneylytics.api.application.port.input.GetThresholdsUseCase
import com.moneylytics.api.application.port.input.SaveThresholdUseCase
import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.domain.Threshold
import org.springframework.stereotype.Service

@Service
class ThresholdService(
    private val thresholdRepository: ThresholdRepository,
) : GetThresholdsUseCase,
    SaveThresholdUseCase,
    DeleteThresholdUseCase {
    override fun getThresholds(userId: Long): List<Threshold> = thresholdRepository.findAllByUserId(userId)

    override fun saveThreshold(
        threshold: Threshold,
        userId: Long,
    ): Threshold = thresholdRepository.upsert(threshold, userId)

    override fun deleteThreshold(
        id: Long,
        userId: Long,
    ) = thresholdRepository.deleteByIdAndUserId(id, userId)
}
