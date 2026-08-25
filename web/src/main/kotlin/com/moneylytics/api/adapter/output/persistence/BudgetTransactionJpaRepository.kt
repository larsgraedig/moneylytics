package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BudgetTransactionJpaRepository : JpaRepository<BudgetTransactionEntity, Long> {
    @Query(
        "SELECT bt FROM BudgetTransactionEntity bt JOIN FETCH bt.transaction WHERE bt.budget.id = :budgetId AND bt.budget.organization.id = :organizationId",
    )
    fun findByBudgetIdAndOrganizationId(
        @Param("budgetId") budgetId: Long,
        @Param("organizationId") organizationId: Long,
    ): List<BudgetTransactionEntity>

    @Query("SELECT bt FROM BudgetTransactionEntity bt JOIN FETCH bt.transaction WHERE bt.budget.organization.id = :organizationId")
    fun findAllByOrganizationId(
        @Param("organizationId") organizationId: Long,
    ): List<BudgetTransactionEntity>

    @Query(
        "SELECT bt FROM BudgetTransactionEntity bt JOIN FETCH bt.budget JOIN FETCH bt.transaction WHERE bt.transaction.id IN :transactionIds",
    )
    fun findByTransactionIds(
        @Param("transactionIds") transactionIds: Collection<Long>,
    ): List<BudgetTransactionEntity>

    @Modifying
    @Query("DELETE FROM BudgetTransactionEntity bt WHERE bt.id = :id AND bt.budget.organization.id = :organizationId")
    fun deleteByIdAndOrganizationId(
        @Param("id") id: Long,
        @Param("organizationId") organizationId: Long,
    )

    fun existsByTransactionId(transactionId: Long): Boolean
}
