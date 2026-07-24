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
    private val userJpaRepository: UserJpaRepository = mock()
    private val transactionJpaRepository: TransactionJpaRepository = mock()
    private val adapter =
        BudgetPersistenceAdapter(budgetJpaRepository, budgetTransactionJpaRepository, userJpaRepository, transactionJpaRepository)

    private val userId = 1L
    private val date = LocalDate.of(2025, 1, 15)
    private val userEntity = UserEntity(externalId = "test@test.de", id = userId)
    private val accountEntity = AccountEntity(iban = "DE00TEST", name = "Test", user = userEntity, id = 10L)

    @Test
    fun `should map budget entity to domain`() {
        val entity = BudgetEntity(user = userEntity, name = "Urlaub", targetAmount = BigDecimal("1200"), note = "Sommer", id = 1L)
        whenever(budgetJpaRepository.findByUserId(userId)).thenReturn(listOf(entity))

        val result = adapter.findAllByUserId(userId)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[0].name).isEqualTo("Urlaub")
        assertThat(result[0].targetAmount).isEqualByComparingTo(BigDecimal("1200"))
        assertThat(result[0].note).isEqualTo("Sommer")
    }

    @Test
    fun `should update budget name, targetAmount and note`() {
        val entity = BudgetEntity(user = userEntity, name = "Old", targetAmount = null, note = null, id = 1L)
        whenever(budgetJpaRepository.findByUserId(userId)).thenReturn(listOf(entity))
        whenever(budgetJpaRepository.save(entity)).thenReturn(entity)

        val updated = Budget(id = 1L, name = "Neu", targetAmount = BigDecimal("2000"), note = "Notiz")
        adapter.update(updated, userId)

        assertThat(entity.name).isEqualTo("Neu")
        assertThat(entity.targetAmount).isEqualByComparingTo(BigDecimal("2000"))
        assertThat(entity.note).isEqualTo("Notiz")
    }

    @Test
    fun `should extract assigned transaction IDs for budget`() {
        val txEntity = txEntity(42L)
        val linkEntity =
            BudgetTransactionEntity(
                budget = BudgetEntity(user = userEntity, name = "Urlaub", id = 1L),
                transaction = txEntity,
                id = 5L,
            )
        whenever(budgetTransactionJpaRepository.findByBudgetIdAndUserId(1L, userId)).thenReturn(listOf(linkEntity))

        val result = adapter.findAssignedTransactionIdsByBudgetId(1L, userId)

        assertThat(result).containsExactly(42L)
    }

    @Test
    fun `should map BudgetTransactionLink from entity`() {
        val txEntity = txEntity(42L, amount = BigDecimal("-300"), accountingDate = date)
        val budgetEntity = BudgetEntity(user = userEntity, name = "Urlaub", id = 1L)
        val linkEntity = BudgetTransactionEntity(budget = budgetEntity, transaction = txEntity, amount = BigDecimal("150"), id = 7L)
        whenever(budgetTransactionJpaRepository.findByBudgetIdAndUserId(1L, userId)).thenReturn(listOf(linkEntity))

        val result = adapter.findTransactionLinksByBudgetId(1L, userId)

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
        user = userEntity,
    )
}
