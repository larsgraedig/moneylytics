package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.BudgetTransactionLink
import java.math.BigDecimal

interface AssignTransactionToBudgetUseCase {
    fun assignTransaction(
        budgetId: Long,
        transactionId: Long,
        amount: BigDecimal?,
        organizationId: Long,
    ): BudgetTransactionLink
}
