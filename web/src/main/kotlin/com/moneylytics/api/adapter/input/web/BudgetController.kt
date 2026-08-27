package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AssignTransactionToBudgetUseCase
import com.moneylytics.api.application.port.input.CreateBudgetUseCase
import com.moneylytics.api.application.port.input.DeleteBudgetUseCase
import com.moneylytics.api.application.port.input.GetBudgetsUseCase
import com.moneylytics.api.application.port.input.RemoveTransactionFromBudgetUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.UpdateBudgetUseCase
import com.moneylytics.api.application.service.BudgetChartPoint
import com.moneylytics.api.domain.Budget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal

@RestController
@RequestMapping("/budgets")
class BudgetController(
    private val getBudgetsUseCase: GetBudgetsUseCase,
    private val createBudgetUseCase: CreateBudgetUseCase,
    private val updateBudgetUseCase: UpdateBudgetUseCase,
    private val deleteBudgetUseCase: DeleteBudgetUseCase,
    private val assignTransactionToBudgetUseCase: AssignTransactionToBudgetUseCase,
    private val removeTransactionFromBudgetUseCase: RemoveTransactionFromBudgetUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    @GetMapping
    suspend fun listBudgets(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): BudgetsResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            BudgetsResponse(getBudgetsUseCase.getBudgets(organizationId).map { it.toDto() })
        }
    }

    @PostMapping
    suspend fun createBudget(
        @RequestBody request: CreateBudgetRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): BudgetDto {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            createBudgetUseCase.createBudget(request.toDomain(), organizationId).toDto()
        }
    }

    @PutMapping("/{id}")
    suspend fun updateBudget(
        @PathVariable id: Long,
        @RequestBody request: UpdateBudgetRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): BudgetDto {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            updateBudgetUseCase
                .updateBudget(
                    Budget(
                        id = id,
                        name = request.name,
                        targetAmount = request.targetAmount,
                        note = request.note,
                    ),
                    organizationId,
                ).toDto()
        }
    }

    @DeleteMapping("/{id}")
    suspend fun deleteBudget(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Unit> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            deleteBudgetUseCase.deleteBudget(id, organizationId)
        }
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/transactions")
    suspend fun assignTransaction(
        @PathVariable id: Long,
        @RequestBody request: AssignTransactionRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): BudgetTransactionLinkDto {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            assignTransactionToBudgetUseCase
                .assignTransaction(
                    budgetId = id,
                    transactionId = request.transactionId,
                    amount = request.amount,
                    organizationId = organizationId,
                ).toDto()
        }
    }

    @DeleteMapping("/transactions/{linkId}")
    suspend fun removeTransactionLink(
        @PathVariable linkId: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Unit> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            removeTransactionFromBudgetUseCase.removeTransactionLink(linkId, organizationId)
        }
        return ResponseEntity.noContent().build()
    }

    private fun com.moneylytics.api.application.service.BudgetWithBalance.toDto() =
        BudgetDto(
            id = requireNotNull(budget.id),
            name = budget.name,
            targetAmount = budget.targetAmount,
            note = budget.note,
            balance = balance,
            transactionLinks = transactionLinks.map { it.toDto() },
            totalContributions = totalContributions,
            chartPoints = chartPoints,
        )

    private fun Budget.toDto() =
        BudgetDto(
            id = requireNotNull(id),
            name = name,
            targetAmount = targetAmount,
            note = note,
            balance = BigDecimal.ZERO,
            transactionLinks = emptyList(),
            totalContributions = BigDecimal.ZERO,
            chartPoints = emptyList(),
        )

    private fun com.moneylytics.api.domain.BudgetTransactionLink.toDto() =
        BudgetTransactionLinkDto(
            id = id,
            transactionId = transactionId,
            amount = amount,
            transactionAmount = transactionAmount,
            effectiveAmount = effectiveAmount(),
            transactionDate = transactionDate.toString(),
            transactionCategory = transactionCategory,
            transactionSubcategory = transactionSubcategory,
            transactionPurpose = transactionPurpose,
            transactionComment = transactionComment,
        )

    private fun CreateBudgetRequest.toDomain() =
        Budget(
            name = name,
            targetAmount = targetAmount,
            note = note,
        )
}

data class BudgetsResponse(
    val budgets: List<BudgetDto>,
)

data class BudgetDto(
    val id: Long,
    val name: String,
    val targetAmount: BigDecimal?,
    val note: String?,
    val balance: BigDecimal,
    val transactionLinks: List<BudgetTransactionLinkDto>,
    val totalContributions: BigDecimal,
    val chartPoints: List<BudgetChartPoint>,
)

data class BudgetTransactionLinkDto(
    val id: Long,
    val transactionId: Long,
    val amount: BigDecimal?,
    val transactionAmount: BigDecimal,
    val effectiveAmount: BigDecimal,
    val transactionDate: String,
    val transactionCategory: String?,
    val transactionSubcategory: String?,
    val transactionPurpose: String?,
    val transactionComment: String?,
)

data class CreateBudgetRequest(
    val name: String,
    val targetAmount: BigDecimal? = null,
    val note: String? = null,
)

data class UpdateBudgetRequest(
    val name: String,
    val targetAmount: BigDecimal? = null,
    val note: String? = null,
)

data class AssignTransactionRequest(
    val transactionId: Long,
    val amount: BigDecimal? = null,
)
