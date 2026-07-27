package com.moneylytics.api.application.port.input

import com.moneylytics.api.application.service.BudgetWithBalance

interface GetBudgetsUseCase {
    fun getBudgets(organizationId: Long): List<BudgetWithBalance>
}
