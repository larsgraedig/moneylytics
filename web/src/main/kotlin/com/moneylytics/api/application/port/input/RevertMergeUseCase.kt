package com.moneylytics.api.application.port.input

data class RevertMergeCommand(
    val mergeId: Long,
    val organizationId: Long,
)

fun interface RevertMergeUseCase {
    fun revertMerge(command: RevertMergeCommand)
}
