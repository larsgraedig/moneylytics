package com.moneylytics.api.application.port.input

import com.moneylytics.api.application.service.BudgetWithBalance

interface GetBudgetsUseCase {
    fun getBudgets(userId: Long): List<BudgetWithBalance>
}
