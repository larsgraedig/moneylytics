package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.AssignTransactionToBudgetUseCase
import com.moneylytics.api.application.port.input.CreateBudgetUseCase
import com.moneylytics.api.application.port.input.DeleteBudgetUseCase
import com.moneylytics.api.application.port.input.GetBudgetsUseCase
import com.moneylytics.api.application.port.input.RemoveTransactionFromBudgetUseCase
import com.moneylytics.api.application.port.input.UpdateBudgetUseCase
import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.domain.Budget
import com.moneylytics.api.domain.BudgetTransactionLink
import org.springframework.stereotype.Service
import java.math.BigDecimal

data class BudgetWithBalance(
    val budget: Budget,
    val balance: BigDecimal,
    val transactionLinks: List<BudgetTransactionLink>,
)

@Service
class BudgetService(
    private val budgetRepository: BudgetRepository,
) : GetBudgetsUseCase,
    CreateBudgetUseCase,
    UpdateBudgetUseCase,
    DeleteBudgetUseCase,
    AssignTransactionToBudgetUseCase,
    RemoveTransactionFromBudgetUseCase {
    override fun getBudgets(userId: Long): List<BudgetWithBalance> {
        val budgets = budgetRepository.findAllByUserId(userId)
        val allLinks = budgetRepository.findAllTransactionLinksByUserId(userId)
        val linksByBudgetId = allLinks.groupBy { it.budgetId }
        return budgets.map { budget ->
            val links = linksByBudgetId[budget.id] ?: emptyList()
            BudgetWithBalance(
                budget = budget,
                balance = links.sumOf { it.effectiveAmount() },
                transactionLinks = links,
            )
        }
    }

    override fun createBudget(
        budget: Budget,
        userId: Long,
    ): Budget = budgetRepository.create(budget, userId)

    override fun updateBudget(
        budget: Budget,
        userId: Long,
    ): Budget = budgetRepository.update(budget, userId)

    override fun deleteBudget(
        id: Long,
        userId: Long,
    ) = budgetRepository.deleteByIdAndUserId(id, userId)

    override fun assignTransaction(
        budgetId: Long,
        transactionId: Long,
        amount: BigDecimal?,
        userId: Long,
    ): BudgetTransactionLink = budgetRepository.assignTransaction(budgetId, transactionId, amount, userId)

    override fun removeTransactionLink(
        linkId: Long,
        userId: Long,
    ) = budgetRepository.removeTransactionLink(linkId, userId)
}
