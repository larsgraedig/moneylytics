package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class CalendarSumsIT : AbstractJpaRepositoryIT() {
    private val march1 = LocalDate.of(2025, 3, 1)
    private val march15 = LocalDate.of(2025, 3, 15)
    private val march31 = LocalDate.of(2025, 3, 31)
    private val april1 = LocalDate.of(2025, 4, 1)

    @Test
    fun `should aggregate expenses by day`() {
        savedTransaction("fp1", accountingDate = march1, amount = BigDecimal("-100.00"))
        savedTransaction("fp2", accountingDate = march1, amount = BigDecimal("-50.00"))
        savedTransaction("fp3", accountingDate = march15, amount = BigDecimal("-200.00"))
        flushAndClear()

        val result = transactionRepo.sumExpensesByDay(organizationId, march1, march31, null)

        val byDay = result.associate { row -> row[0] as LocalDate to (row[1] as BigDecimal) }
        assertThat(byDay).hasSize(2)
        assertThat(byDay[march1]!!.abs()).isEqualByComparingTo(BigDecimal("150.00"))
        assertThat(byDay[march15]!!.abs()).isEqualByComparingTo(BigDecimal("200.00"))
    }

    @Test
    fun `should exclude income transactions`() {
        savedTransaction("fp1", accountingDate = march1, amount = BigDecimal("3000.00"))
        savedTransaction("fp2", accountingDate = march1, amount = BigDecimal("-120.00"))
        flushAndClear()

        val result = transactionRepo.sumExpensesByDay(organizationId, march1, march31, null)

        assertThat(result).hasSize(1)
        val day = result.single()
        assertThat((day[1] as BigDecimal).abs()).isEqualByComparingTo(BigDecimal("120.00"))
    }

    @Test
    fun `should exclude transactions outside range`() {
        savedTransaction("fp1", accountingDate = march1, amount = BigDecimal("-100.00"))
        savedTransaction("fp2", accountingDate = april1, amount = BigDecimal("-200.00"))
        flushAndClear()

        val result = transactionRepo.sumExpensesByDay(organizationId, march1, march31, null)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `should exclude transactions of other organizations`() {
        savedTransaction("fp1", accountingDate = march1, amount = BigDecimal("-100.00"))
        val otherAccount =
            accountRepo.save(
                AccountEntity(iban = "DE00OTHER000000000001", name = "Fremdes Konto", organization = otherOrganization),
            )
        transactionRepo.save(
            TransactionEntity(
                accountingDate = march1,
                bookingDate = march1,
                valueDate = march1,
                amount = BigDecimal("-500.00"),
                currency = "EUR",
                account = otherAccount,
                fingerprint = "fp-other",
                organization = otherOrganization,
            ),
        )
        flushAndClear()

        val result = transactionRepo.sumExpensesByDay(organizationId, march1, march31, null)

        assertThat(result).hasSize(1)
        assertThat((result.single()[1] as BigDecimal).abs()).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `should filter by iban when specified`() {
        val otherAccount =
            accountRepo.save(
                AccountEntity(iban = "DE00OTHER000000000002", name = "Sparkasse", organization = organization),
            )
        savedTransaction("fp1", accountingDate = march1, amount = BigDecimal("-100.00"))
        transactionRepo.save(
            TransactionEntity(
                accountingDate = march1,
                bookingDate = march1,
                valueDate = march1,
                amount = BigDecimal("-500.00"),
                currency = "EUR",
                account = otherAccount,
                fingerprint = "fp-other-acc",
                organization = organization,
            ),
        )
        flushAndClear()

        val result = transactionRepo.sumExpensesByDay(organizationId, march1, march31, account.iban)

        assertThat(result).hasSize(1)
        assertThat((result.single()[1] as BigDecimal).abs()).isEqualByComparingTo(BigDecimal("100.00"))
    }
}
