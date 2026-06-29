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
    private val userJpaRepository: UserJpaRepository,
    private val transactionJpaRepository: TransactionJpaRepository,
) : BudgetRepository {
    override fun findAllByUserId(userId: Long): List<Budget> = budgetJpaRepository.findByUserId(userId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findTransactionLinksByBudgetId(
        budgetId: Long,
        userId: Long,
    ): List<BudgetTransactionLink> = budgetTransactionJpaRepository.findByBudgetIdAndUserId(budgetId, userId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findAllTransactionLinksByUserId(userId: Long): List<BudgetTransactionLink> =
        budgetTransactionJpaRepository.findAllByUserId(userId).map { it.toDomain() }

    @Transactional
    override fun create(
        budget: Budget,
        userId: Long,
    ): Budget =
        budgetJpaRepository
            .save(
                BudgetEntity(
                    user = userJpaRepository.getReferenceById(userId),
                    name = budget.name,
                    targetAmount = budget.targetAmount,
                    note = budget.note,
                ),
            ).toDomain()

    @Transactional
    override fun update(
        budget: Budget,
        userId: Long,
    ): Budget {
        val entity = budgetJpaRepository.findByUserId(userId).first { it.id == budget.id }
        entity.name = budget.name
        entity.targetAmount = budget.targetAmount
        entity.note = budget.note
        return budgetJpaRepository.save(entity).toDomain()
    }

    @Transactional
    override fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    ) = budgetJpaRepository.deleteByIdAndUserId(id, userId)

    @Transactional
    override fun assignTransaction(
        budgetId: Long,
        transactionId: Long,
        amount: BigDecimal?,
        userId: Long,
    ): BudgetTransactionLink {
        val budget = budgetJpaRepository.findByUserId(userId).first { it.id == budgetId }
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
        userId: Long,
    ) = budgetTransactionJpaRepository.deleteByIdAndUserId(linkId, userId)

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
            transactionCategory = transaction.category,
            transactionSubcategory = transaction.subcategory,
            transactionPurpose = transaction.purpose,
            transactionComment = transaction.comment,
        )
}
