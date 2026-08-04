package com.moneylytics.api.application.port.input

data class MoveCategoryCommand(
    val id: Long,
    val newParentId: Long?,
    val organizationId: Long,
)

fun interface MoveCategoryUseCase {
    fun moveCategory(command: MoveCategoryCommand)
}
