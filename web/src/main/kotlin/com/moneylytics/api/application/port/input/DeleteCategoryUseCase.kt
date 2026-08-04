package com.moneylytics.api.application.port.input

fun interface DeleteCategoryUseCase {
    fun deleteCategory(
        id: Long,
        organizationId: Long,
    )
}
