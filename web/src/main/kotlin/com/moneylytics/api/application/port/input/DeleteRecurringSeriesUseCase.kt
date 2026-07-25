package com.moneylytics.api.application.port.input

fun interface DeleteRecurringSeriesUseCase {
    fun delete(command: DeleteRecurringSeriesCommand)
}

data class DeleteRecurringSeriesCommand(
    val userId: Long,
    val seriesId: Long,
)
