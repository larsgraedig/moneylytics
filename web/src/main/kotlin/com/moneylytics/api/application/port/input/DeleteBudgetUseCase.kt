package com.moneylytics.api.application.port.input

interface DeleteBudgetUseCase {
    fun deleteBudget(
        id: Long,
        userId: Long,
    )
}
