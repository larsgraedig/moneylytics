package com.moneylytics.api.application.port.input

data class RenameCategoryCommand(
    val id: Long,
    val newName: String,
    val organizationId: Long,
)

fun interface RenameCategoryUseCase {
    fun renameCategory(command: RenameCategoryCommand)
}
