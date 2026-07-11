package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface BudgetTransactionJpaRepository : JpaRepository<BudgetTransactionEntity, Long> {
    @Query(
        "SELECT bt FROM BudgetTransactionEntity bt JOIN FETCH bt.transaction WHERE bt.budget.id = :budgetId AND bt.budget.user.id = :userId",
    )
    fun findByBudgetIdAndUserId(
        budgetId: Long,
        userId: Long,
    ): List<BudgetTransactionEntity>

    @Query("SELECT bt FROM BudgetTransactionEntity bt JOIN FETCH bt.transaction WHERE bt.budget.user.id = :userId")
    fun findAllByUserId(userId: Long): List<BudgetTransactionEntity>

    @Query(
        "SELECT bt FROM BudgetTransactionEntity bt JOIN FETCH bt.budget JOIN FETCH bt.transaction WHERE bt.transaction.id IN :transactionIds",
    )
    fun findByTransactionIds(
        @org.springframework.data.repository.query.Param("transactionIds") transactionIds: Collection<Long>,
    ): List<BudgetTransactionEntity>

    @Modifying
    @Query("DELETE FROM BudgetTransactionEntity bt WHERE bt.id = :id AND bt.budget.user.id = :userId")
    fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    )
}
