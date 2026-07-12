package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class BudgetJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var budgetRepo: BudgetJpaRepository

    private fun savedBudget(
        name: String = "Urlaub 2025",
        forUser: UserEntity = user,
    ) = budgetRepo.save(BudgetEntity(user = forUser, name = name, targetAmount = BigDecimal("1000")))

    @Test
    fun `should find all budgets for user`() {
        savedBudget(name = "Urlaub")
        savedBudget(name = "Neue Küche")
        savedBudget(name = "Fremdes Budget", forUser = otherUser)

        val result = budgetRepo.findByUserId(user.id!!)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("Urlaub", "Neue Küche")
    }

    @Test
    fun `should return empty list when user has no budgets`() {
        val result = budgetRepo.findByUserId(otherUser.id!!)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should delete budget by id and user id`() {
        val budget = savedBudget()
        flushAndClear()

        budgetRepo.deleteByIdAndUserId(budget.id!!, user.id!!)
        flushAndClear()

        assertThat(budgetRepo.findById(budget.id!!)).isEmpty
    }

    @Test
    fun `should not delete budget belonging to other user`() {
        val otherBudget = savedBudget(forUser = otherUser)
        flushAndClear()

        budgetRepo.deleteByIdAndUserId(otherBudget.id!!, user.id!!)
        flushAndClear()

        assertThat(budgetRepo.findById(otherBudget.id!!)).isPresent
    }
}
