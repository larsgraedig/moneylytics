package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Threshold

interface GetThresholdsUseCase {
    fun getThresholds(organizationId: Long): List<Threshold>
}
