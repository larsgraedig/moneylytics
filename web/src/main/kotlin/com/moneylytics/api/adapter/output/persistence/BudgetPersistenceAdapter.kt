package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.domain.Budget
import com.moneylytics.api.domain.BudgetTransactionLink
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class BudgetPersistenceAdapter(
    private val budgetJpaRepository: BudgetJpaRepository,
    private val budgetTransactionJpaRepository: BudgetTransactionJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
    private val transactionJpaRepository: TransactionJpaRepository,
) : BudgetRepository {
    override fun findAllByOrganizationId(organizationId: Long): List<Budget> =
        budgetJpaRepository.findByOrganizationId(organizationId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findTransactionLinksByBudgetId(
        budgetId: Long,
        organizationId: Long,
    ): List<BudgetTransactionLink> =
        budgetTransactionJpaRepository.findByBudgetIdAndOrganizationId(budgetId, organizationId).map {
            it.toDomain()
        }

    @Transactional(readOnly = true)
    override fun findAllTransactionLinksByOrganizationId(organizationId: Long): List<BudgetTransactionLink> =
        budgetTransactionJpaRepository.findAllByOrganizationId(organizationId).map { it.toDomain() }

    @Transactional
    override fun create(
        budget: Budget,
        organizationId: Long,
    ): Budget =
        budgetJpaRepository
            .save(
                BudgetEntity(
                    organization = organizationJpaRepository.getReferenceById(organizationId),
                    name = budget.name,
                    targetAmount = budget.targetAmount,
                    note = budget.note,
                ),
            ).toDomain()

    @Transactional
    override fun update(
        budget: Budget,
        organizationId: Long,
    ): Budget {
        val entity = budgetJpaRepository.findByOrganizationId(organizationId).first { it.id == budget.id }
        entity.name = budget.name
        entity.targetAmount = budget.targetAmount
        entity.note = budget.note
        return budgetJpaRepository.save(entity).toDomain()
    }

    @Transactional
    override fun deleteByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ) = budgetJpaRepository.deleteByIdAndOrganizationId(id, organizationId)

    @Transactional
    override fun assignTransaction(
        budgetId: Long,
        transactionId: Long,
        amount: BigDecimal?,
        organizationId: Long,
    ): BudgetTransactionLink {
        val budget = budgetJpaRepository.findByOrganizationId(organizationId).first { it.id == budgetId }
        val transaction = transactionJpaRepository.getReferenceById(transactionId)
        return budgetTransactionJpaRepository
            .save(
                BudgetTransactionEntity(
                    budget = budget,
                    transaction = transaction,
                    amount = amount,
                ),
            ).toDomain()
    }

    @Transactional
    override fun removeTransactionLink(
        linkId: Long,
        organizationId: Long,
    ) = budgetTransactionJpaRepository.deleteByIdAndOrganizationId(linkId, organizationId)

    @Transactional(readOnly = true)
    override fun findAssignedTransactionIdsByBudgetId(
        budgetId: Long,
        organizationId: Long,
    ): Set<Long> =
        budgetTransactionJpaRepository
            .findByBudgetIdAndOrganizationId(budgetId, organizationId)
            .mapNotNull { it.transaction.id }
            .toHashSet()

    private fun BudgetEntity.toDomain() =
        Budget(
            id = id,
            name = name,
            targetAmount = targetAmount,
            note = note,
        )

    private fun BudgetTransactionEntity.toDomain() =
        BudgetTransactionLink(
            id = id!!,
            budgetId = budget.id!!,
            transactionId = transaction.id!!,
            amount = amount,
            transactionAmount = transaction.amount,
            transactionDate = transaction.accountingDate,
            transactionCategory = transaction.category?.name,
            transactionSubcategory = transaction.category?.groupName,
            transactionPurpose = transaction.purpose,
            transactionComment = transaction.comment,
        )
}
