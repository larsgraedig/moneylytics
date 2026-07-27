package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.RecurringSeries

fun interface ConfirmRecurringSeriesUseCase {
    fun confirm(command: ConfirmRecurringSeriesCommand): List<RecurringSeries>
}

data class ConfirmRecurringSeriesCommand(
    val organizationId: Long,
    val confirmedFingerprints: List<String>,
    val falsePositiveFingerprints: List<String>,
    val lookbackMonths: Long = 24,
)
