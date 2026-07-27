package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Budget

interface UpdateBudgetUseCase {
    fun updateBudget(
        budget: Budget,
        organizationId: Long,
    ): Budget
}
