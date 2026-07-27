package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.Budget
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class BudgetPersistenceAdapterTest {
    private val budgetJpaRepository: BudgetJpaRepository = mock()
    private val budgetTransactionJpaRepository: BudgetTransactionJpaRepository = mock()
    private val organizationJpaRepository: OrganizationJpaRepository = mock()
    private val transactionJpaRepository: TransactionJpaRepository = mock()
    private val adapter =
        BudgetPersistenceAdapter(budgetJpaRepository, budgetTransactionJpaRepository, organizationJpaRepository, transactionJpaRepository)

    private val organizationId = 1L
    private val date = LocalDate.of(2025, 1, 15)
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)
    private val accountEntity = AccountEntity(iban = "DE00TEST", name = "Test", organization = organizationEntity, id = 10L)

    @Test
    fun `should map budget entity to domain`() {
        val entity =
            BudgetEntity(organization = organizationEntity, name = "Urlaub", targetAmount = BigDecimal("1200"), note = "Sommer", id = 1L)
        whenever(budgetJpaRepository.findByOrganizationId(organizationId)).thenReturn(listOf(entity))

        val result = adapter.findAllByOrganizationId(organizationId)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[0].name).isEqualTo("Urlaub")
        assertThat(result[0].targetAmount).isEqualByComparingTo(BigDecimal("1200"))
        assertThat(result[0].note).isEqualTo("Sommer")
    }

    @Test
    fun `should update budget name, targetAmount and note`() {
        val entity = BudgetEntity(organization = organizationEntity, name = "Old", targetAmount = null, note = null, id = 1L)
        whenever(budgetJpaRepository.findByOrganizationId(organizationId)).thenReturn(listOf(entity))
        whenever(budgetJpaRepository.save(entity)).thenReturn(entity)

        val updated = Budget(id = 1L, name = "Neu", targetAmount = BigDecimal("2000"), note = "Notiz")
        adapter.update(updated, organizationId)

        assertThat(entity.name).isEqualTo("Neu")
        assertThat(entity.targetAmount).isEqualByComparingTo(BigDecimal("2000"))
        assertThat(entity.note).isEqualTo("Notiz")
    }

    @Test
    fun `should extract assigned transaction IDs for budget`() {
        val txEntity = txEntity(42L)
        val linkEntity =
            BudgetTransactionEntity(
                budget = BudgetEntity(organization = organizationEntity, name = "Urlaub", id = 1L),
                transaction = txEntity,
                id = 5L,
            )
        whenever(budgetTransactionJpaRepository.findByBudgetIdAndOrganizationId(1L, organizationId)).thenReturn(listOf(linkEntity))

        val result = adapter.findAssignedTransactionIdsByBudgetId(1L, organizationId)

        assertThat(result).containsExactly(42L)
    }

    @Test
    fun `should map BudgetTransactionLink from entity`() {
        val txEntity = txEntity(42L, amount = BigDecimal("-300"), accountingDate = date)
        val budgetEntity = BudgetEntity(organization = organizationEntity, name = "Urlaub", id = 1L)
        val linkEntity = BudgetTransactionEntity(budget = budgetEntity, transaction = txEntity, amount = BigDecimal("150"), id = 7L)
        whenever(budgetTransactionJpaRepository.findByBudgetIdAndOrganizationId(1L, organizationId)).thenReturn(listOf(linkEntity))

        val result = adapter.findTransactionLinksByBudgetId(1L, organizationId)

        assertThat(result).hasSize(1)
        val link = result[0]
        assertThat(link.id).isEqualTo(7L)
        assertThat(link.budgetId).isEqualTo(1L)
        assertThat(link.transactionId).isEqualTo(42L)
        assertThat(link.amount).isEqualByComparingTo(BigDecimal("150"))
        assertThat(link.transactionAmount).isEqualByComparingTo(BigDecimal("-300"))
        assertThat(link.transactionDate).isEqualTo(date)
    }

    private fun txEntity(
        id: Long,
        amount: BigDecimal = BigDecimal("-100"),
        accountingDate: LocalDate = date,
    ) = TransactionEntity(
        id = id,
        category = null,
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = accountingDate,
        amount = amount,
        currency = "EUR",
        account = accountEntity,
        fingerprint = "fp$id",
        organization = organizationEntity,
    )
}
