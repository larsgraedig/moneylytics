package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class BudgetTransactionJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var budgetRepo: BudgetJpaRepository

    @Autowired private lateinit var budgetTransactionRepo: BudgetTransactionJpaRepository

    private fun savedBudget(forOrganization: OrganizationEntity = organization) =
        budgetRepo.save(BudgetEntity(organization = forOrganization, name = "Test Budget", targetAmount = BigDecimal("500")))

    private fun savedLink(
        budget: BudgetEntity,
        tx: TransactionEntity,
        amount: BigDecimal? = null,
    ) = budgetTransactionRepo.save(BudgetTransactionEntity(budget = budget, transaction = tx, amount = amount))

    @Test
    fun `should find budget transactions for given budget and user`() {
        val budget = savedBudget()
        val budgetId = checkNotNull(budget.id)
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedLink(budget, tx1)
        savedLink(budget, tx2)

        val result = budgetTransactionRepo.findByBudgetIdAndOrganizationId(budgetId, organizationId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.transaction.fingerprint }).containsExactlyInAnyOrder("fp-1", "fp-2")
    }

    @Test
    fun `should not return links belonging to other user`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER00000000002", name = "Fremd", organization = otherOrganization))
        val budget = savedBudget()
        val budgetId = checkNotNull(budget.id)
        val otherBudget = savedBudget(forOrganization = otherOrganization)
        val tx = savedTransaction("fp-mine")
        val otherTx = savedTransaction("fp-theirs", forAccount = otherAccount, forOrganization = otherOrganization)
        savedLink(budget, tx)
        savedLink(otherBudget, otherTx)

        val result = budgetTransactionRepo.findByBudgetIdAndOrganizationId(budgetId, organizationId)

        assertThat(result).hasSize(1)
        assertThat(result.first().transaction.fingerprint).isEqualTo("fp-mine")
    }

    @Test
    fun `should find all budget transactions for user across budgets`() {
        val budget1 = savedBudget()
        val budget2 = savedBudget()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedLink(budget1, tx1)
        savedLink(budget2, tx2)

        val result = budgetTransactionRepo.findAllByOrganizationId(organizationId)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `should find budget transactions by transaction ids`() {
        val budget = savedBudget()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx3 = savedTransaction("fp-3")
        val tx1Id = checkNotNull(tx1.id)
        val tx3Id = checkNotNull(tx3.id)
        val link1 = savedLink(budget, tx1)
        savedLink(budget, tx2)

        val result = budgetTransactionRepo.findByTransactionIds(listOf(tx1Id, tx3Id))

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo(link1.id)
    }

    @Test
    fun `should store and return partial allocation amount`() {
        val budget = savedBudget()
        val budgetId = checkNotNull(budget.id)
        val tx = savedTransaction("fp-1", amount = BigDecimal("-800.00"))
        savedLink(budget, tx, amount = BigDecimal("300"))

        val result = budgetTransactionRepo.findByBudgetIdAndOrganizationId(budgetId, organizationId)

        assertThat(result.first().amount).isEqualByComparingTo(BigDecimal("300"))
    }

    @Test
    fun `should delete budget transaction link by id and user id`() {
        val budget = savedBudget()
        val tx = savedTransaction("fp-1")
        val link = savedLink(budget, tx)
        val linkId = checkNotNull(link.id)
        flushAndClear()

        budgetTransactionRepo.deleteByIdAndOrganizationId(linkId, organizationId)
        flushAndClear()

        assertThat(budgetTransactionRepo.findById(linkId)).isEmpty
    }

    @Test
    fun `should not delete link belonging to other user`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER00000000002", name = "Fremd", organization = otherOrganization))
        val otherBudget = savedBudget(forOrganization = otherOrganization)
        val otherTx = savedTransaction("fp-theirs", forAccount = otherAccount, forOrganization = otherOrganization)
        val otherLink = savedLink(otherBudget, otherTx)
        val otherLinkId = checkNotNull(otherLink.id)
        flushAndClear()

        budgetTransactionRepo.deleteByIdAndOrganizationId(otherLinkId, organizationId)
        flushAndClear()

        assertThat(budgetTransactionRepo.findById(otherLinkId)).isPresent
    }
}
