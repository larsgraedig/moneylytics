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

data class BudgetChartPoint(
    val date: String,
    val cumulative: BigDecimal,
)

data class BudgetWithBalance(
    val budget: Budget,
    val balance: BigDecimal,
    val transactionLinks: List<BudgetTransactionLink>,
    val totalContributions: BigDecimal,
    val chartPoints: List<BudgetChartPoint>,
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
    override fun getBudgets(organizationId: Long): List<BudgetWithBalance> {
        val budgets = budgetRepository.findAllByOrganizationId(organizationId)
        val allLinks = budgetRepository.findAllTransactionLinksByOrganizationId(organizationId)
        val linksByBudgetId = allLinks.groupBy { it.budgetId }
        return budgets.map { budget ->
            val links = linksByBudgetId[budget.id] ?: emptyList()
            val sortedLinks = links.sortedBy { it.transactionDate }
            var cumulative = BigDecimal.ZERO
            val chartPoints =
                sortedLinks.map { link ->
                    cumulative += effectiveContrib(link)
                    BudgetChartPoint(date = link.transactionDate.toString(), cumulative = cumulative)
                }
            BudgetWithBalance(
                budget = budget,
                balance = links.sumOf { it.effectiveAmount() },
                transactionLinks = links,
                totalContributions = cumulative,
                chartPoints = chartPoints,
            )
        }
    }

    private fun effectiveContrib(link: BudgetTransactionLink): BigDecimal = link.effectiveAmount()

    override fun createBudget(
        budget: Budget,
        organizationId: Long,
    ): Budget = budgetRepository.create(budget, organizationId)

    override fun updateBudget(
        budget: Budget,
        organizationId: Long,
    ): Budget = budgetRepository.update(budget, organizationId)

    override fun deleteBudget(
        id: Long,
        organizationId: Long,
    ) = budgetRepository.deleteByIdAndOrganizationId(id, organizationId)

    override fun assignTransaction(
        budgetId: Long,
        transactionId: Long,
        amount: BigDecimal?,
        organizationId: Long,
    ): BudgetTransactionLink = budgetRepository.assignTransaction(budgetId, transactionId, amount, organizationId)

    override fun removeTransactionLink(
        linkId: Long,
        organizationId: Long,
    ) = budgetRepository.removeTransactionLink(linkId, organizationId)
}
