package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.ThresholdPeriod
import com.moneylytics.api.domain.ThresholdStatus
import java.math.BigDecimal
import java.time.LocalDate

data class GetThresholdStatusQuery(
    val from: LocalDate,
    val to: LocalDate,
    val organizationId: Long,
    val accountIban: String? = null,
)

data class ThresholdStatusItem(
    val thresholdId: Long,
    val categoryId: Long,
    val categoryPath: List<String>,
    val period: ThresholdPeriod,
    val notice: BigDecimal?,
    val warning: BigDecimal?,
    val critical: BigDecimal?,
    val spending: BigDecimal,
    val periodsInRange: BigDecimal,
    val pct: BigDecimal,
    val tickNotice: BigDecimal?,
    val tickWarning: BigDecimal?,
    val status: ThresholdStatus,
)

data class ThresholdStatusResponse(
    val items: List<ThresholdStatusItem>,
)

fun interface GetThresholdStatusUseCase {
    fun getThresholdStatus(query: GetThresholdStatusQuery): ThresholdStatusResponse
}
