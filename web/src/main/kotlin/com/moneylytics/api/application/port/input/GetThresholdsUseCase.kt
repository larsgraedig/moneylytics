package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Threshold

interface GetThresholdsUseCase {
    fun getThresholds(userId: Long): List<Threshold>
}
