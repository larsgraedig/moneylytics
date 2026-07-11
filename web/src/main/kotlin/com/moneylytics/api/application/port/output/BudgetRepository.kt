package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Budget
import com.moneylytics.api.domain.BudgetTransactionLink

interface BudgetRepository {
    fun findAllByUserId(userId: Long): List<Budget>

    fun findTransactionLinksByBudgetId(
        budgetId: Long,
        userId: Long,
    ): List<BudgetTransactionLink>

    fun findAllTransactionLinksByUserId(userId: Long): List<BudgetTransactionLink>

    fun create(
        budget: Budget,
        userId: Long,
    ): Budget

    fun update(
        budget: Budget,
        userId: Long,
    ): Budget

    fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    )

    fun assignTransaction(
        budgetId: Long,
        transactionId: Long,
        amount: java.math.BigDecimal?,
        userId: Long,
    ): BudgetTransactionLink

    fun removeTransactionLink(
        linkId: Long,
        userId: Long,
    )

    fun findAssignedTransactionIdsByBudgetId(
        budgetId: Long,
        userId: Long,
    ): Set<Long>
}
