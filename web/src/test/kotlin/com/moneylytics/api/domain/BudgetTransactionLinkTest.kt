package com.moneylytics.api.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class BudgetTransactionLinkTest {
    private val date = LocalDate.of(2025, 1, 15)

    @Test
    fun `should return transactionAmount when no custom amount is set`() {
        val link = link(amount = null, transactionAmount = BigDecimal("-300"))

        assertThat(link.effectiveAmount()).isEqualByComparingTo(BigDecimal("-300"))
    }

    @Test
    fun `should negate custom amount for expense transactions`() {
        val link = link(amount = BigDecimal("150"), transactionAmount = BigDecimal("-300"))

        // transactionAmount < 0 → negate abs(amount) → -150
        assertThat(link.effectiveAmount()).isEqualByComparingTo(BigDecimal("-150"))
    }

    @Test
    fun `should keep custom amount positive for income transactions`() {
        val link = link(amount = BigDecimal("150"), transactionAmount = BigDecimal("300"))

        // transactionAmount > 0 → abs(amount) → +150
        assertThat(link.effectiveAmount()).isEqualByComparingTo(BigDecimal("150"))
    }

    @Test
    fun `should return positive custom amount when transactionAmount is zero`() {
        val link = link(amount = BigDecimal("50"), transactionAmount = BigDecimal.ZERO)

        // signum() = 0, not < 0 → abs(amount) → +50
        assertThat(link.effectiveAmount()).isEqualByComparingTo(BigDecimal("50"))
    }

    private fun link(
        amount: BigDecimal?,
        transactionAmount: BigDecimal,
    ) = BudgetTransactionLink(
        id = 1L,
        budgetId = 10L,
        transactionId = 20L,
        amount = amount,
        transactionAmount = transactionAmount,
        transactionDate = date,
        transactionCategory = null,
        transactionSubcategory = null,
        transactionPurpose = null,
        transactionComment = null,
    )
}
