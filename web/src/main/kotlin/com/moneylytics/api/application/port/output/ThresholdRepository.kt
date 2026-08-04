package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Threshold

interface ThresholdRepository {
    fun findAllByOrganizationId(organizationId: Long): List<Threshold>

    fun upsert(
        threshold: Threshold,
        organizationId: Long,
    ): Threshold

    fun deleteByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    )

    fun existsByCategoryId(
        categoryId: Long,
        organizationId: Long,
    ): Boolean
}
