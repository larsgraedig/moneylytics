package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Budget

interface CreateBudgetUseCase {
    fun createBudget(
        budget: Budget,
        userId: Long,
    ): Budget
}
