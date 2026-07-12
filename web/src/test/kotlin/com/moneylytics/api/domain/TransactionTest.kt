package com.moneylytics.api.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionTest {
    private val date = LocalDate.of(2025, 1, 15)

    @Test
    fun `should return original amount when no offset links`() {
        val tx = tx(amount = BigDecimal("-100"))

        assertThat(tx.effectiveAmount()).isEqualByComparingTo(BigDecimal("-100"))
    }

    @Test
    fun `should reduce expense by minOf amountA and amountB when both are set`() {
        val link = offsetLink(amountA = BigDecimal("-60"), amountB = BigDecimal("60"), linkedAmount = BigDecimal("60"))
        val tx = tx(amount = BigDecimal("-100"), links = listOf(link))

        // minOf(60, 60) = 60; effectiveAmount = -100 + 60 = -40
        assertThat(tx.effectiveAmount()).isEqualByComparingTo(BigDecimal("-40"))
    }

    @Test
    fun `should use smaller value when amountA and amountB differ`() {
        val link = offsetLink(amountA = BigDecimal("-30"), amountB = BigDecimal("80"), linkedAmount = BigDecimal("80"))
        val tx = tx(amount = BigDecimal("-100"), links = listOf(link))

        // minOf(30, 80) = 30; effectiveAmount = -100 + 30 = -70
        assertThat(tx.effectiveAmount()).isEqualByComparingTo(BigDecimal("-70"))
    }

    @Test
    fun `should contribute zero when both amountA and amountB are null`() {
        val link = offsetLink(amountA = null, amountB = null, linkedAmount = BigDecimal("50"), myCommitted = BigDecimal("-100"))
        val tx = tx(amount = BigDecimal("-100"), links = listOf(link))

        // a==null && b==null → ZERO; effectiveAmount = -100 + 0 = -100
        assertThat(tx.effectiveAmount()).isEqualByComparingTo(BigDecimal("-100"))
    }

    @Test
    fun `should use minOf myCommitted and linkedAmount when exactly one side is null`() {
        // amountB is null → else branch: minOf(myCommitted.abs(), linkedAmount.abs())
        val link = offsetLink(amountA = BigDecimal("-80"), amountB = null, linkedAmount = BigDecimal("50"), myCommitted = BigDecimal("-80"))
        val tx = tx(amount = BigDecimal("-100"), links = listOf(link))

        // minOf(80, 50) = 50; effectiveAmount = -100 + 50 = -50
        assertThat(tx.effectiveAmount()).isEqualByComparingTo(BigDecimal("-50"))
    }

    @Test
    fun `should reduce income by offset`() {
        val link = offsetLink(amountA = BigDecimal("60"), amountB = BigDecimal("60"), linkedAmount = BigDecimal("-60"))
        val tx = tx(amount = BigDecimal("100"), links = listOf(link))

        // income: effectiveAmount = 100 - 60 = 40
        assertThat(tx.effectiveAmount()).isEqualByComparingTo(BigDecimal("40"))
    }

    @Test
    fun `should sum offsets from multiple links`() {
        val link1 = offsetLink(amountA = BigDecimal("-30"), amountB = BigDecimal("30"), linkedAmount = BigDecimal("30"))
        val link2 = offsetLink(amountA = BigDecimal("-25"), amountB = BigDecimal("25"), linkedAmount = BigDecimal("25"))
        val tx = tx(amount = BigDecimal("-100"), links = listOf(link1, link2))

        // totalOffset = 30 + 25 = 55; effectiveAmount = -100 + 55 = -45
        assertThat(tx.effectiveAmount()).isEqualByComparingTo(BigDecimal("-45"))
    }

    private fun tx(
        amount: BigDecimal,
        links: List<TransactionOffsetLink> = emptyList(),
    ) = Transaction(
        category = null,
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00TEST",
        offsetLinks = links,
    )

    private fun offsetLink(
        amountA: BigDecimal?,
        amountB: BigDecimal?,
        linkedAmount: BigDecimal,
        myCommitted: BigDecimal = amountA ?: BigDecimal.ZERO,
    ) = TransactionOffsetLink(
        id = 1L,
        linkedTransactionId = 99L,
        linkedTransactionAmount = linkedAmount,
        amountA = amountA,
        amountB = amountB,
        myCommitted = myCommitted,
        groupId = null,
    )
}
