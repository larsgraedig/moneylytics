package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Budget
import com.moneylytics.api.domain.BudgetTransactionLink

interface BudgetRepository {
    fun findAllByOrganizationId(organizationId: Long): List<Budget>

    fun findTransactionLinksByBudgetId(
        budgetId: Long,
        organizationId: Long,
    ): List<BudgetTransactionLink>

    fun findAllTransactionLinksByOrganizationId(organizationId: Long): List<BudgetTransactionLink>

    fun create(
        budget: Budget,
        organizationId: Long,
    ): Budget

    fun update(
        budget: Budget,
        organizationId: Long,
    ): Budget

    fun deleteByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    )

    fun assignTransaction(
        budgetId: Long,
        transactionId: Long,
        amount: java.math.BigDecimal?,
        organizationId: Long,
    ): BudgetTransactionLink

    fun removeTransactionLink(
        linkId: Long,
        organizationId: Long,
    )

    fun findAssignedTransactionIdsByBudgetId(
        budgetId: Long,
        organizationId: Long,
    ): Set<Long>
}
