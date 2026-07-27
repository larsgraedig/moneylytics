package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class BudgetJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var budgetRepo: BudgetJpaRepository

    private fun savedBudget(
        name: String = "Urlaub 2025",
        forOrganization: OrganizationEntity = organization,
    ) = budgetRepo.save(BudgetEntity(organization = forOrganization, name = name, targetAmount = BigDecimal("1000")))

    @Test
    fun `should find all budgets for user`() {
        savedBudget(name = "Urlaub")
        savedBudget(name = "Neue Küche")
        savedBudget(name = "Fremdes Budget", forOrganization = otherOrganization)

        val result = budgetRepo.findByOrganizationId(organizationId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("Urlaub", "Neue Küche")
    }

    @Test
    fun `should return empty list when user has no budgets`() {
        val result = budgetRepo.findByOrganizationId(otherOrganizationId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should delete budget by id and user id`() {
        val budget = savedBudget()
        val budgetId = checkNotNull(budget.id)
        flushAndClear()

        budgetRepo.deleteByIdAndOrganizationId(budgetId, organizationId)
        flushAndClear()

        assertThat(budgetRepo.findById(budgetId)).isEmpty
    }

    @Test
    fun `should not delete budget belonging to other user`() {
        val otherBudget = savedBudget(forOrganization = otherOrganization)
        val otherBudgetId = checkNotNull(otherBudget.id)
        flushAndClear()

        budgetRepo.deleteByIdAndOrganizationId(otherBudgetId, organizationId)
        flushAndClear()

        assertThat(budgetRepo.findById(otherBudgetId)).isPresent
    }
}
