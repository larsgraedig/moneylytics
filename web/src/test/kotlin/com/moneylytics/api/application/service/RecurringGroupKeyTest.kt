package com.moneylytics.api.application.service

import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class RecurringGroupKeyTest {
    private val date = LocalDate.of(2024, 1, 1)

    private fun tx(
        amount: String = "-50.00",
        iban: String = "DE01",
        counterpartyIban: String? = null,
        counterpartyName: String? = null,
        purpose: String? = null,
    ) = Transaction(
        category = null,
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = BigDecimal(amount),
        currency = "EUR",
        accountIban = iban,
        counterpartyIban = counterpartyIban,
        counterpartyName = counterpartyName,
        purpose = purpose,
    )

    @Test
    fun `should use counterpartyIban as identifier when present`() {
        val result = groupKey(tx(counterpartyIban = "DE99123", counterpartyName = "Ignored", purpose = "Also ignored"))

        assertThat(result).isEqualTo("DE01|E|DE99123")
    }

    @Test
    fun `should fall back to normalized counterparty name when no counterparty IBAN`() {
        val result = groupKey(tx(counterpartyName = "Netflix GmbH!"))

        assertThat(result).isEqualTo("DE01|E|netflix gmbh")
    }

    @Test
    fun `should fall back to normalized purpose when no counterparty`() {
        val result = groupKey(tx(purpose = "Miete Januar 2024"))

        assertThat(result).isEqualTo("DE01|E|miete januar")
    }

    @Test
    fun `should use unknown when no identifier available`() {
        val result = groupKey(tx())

        assertThat(result).isEqualTo("DE01|E|unknown")
    }

    @Test
    fun `should mark negative amount as expense direction`() {
        val result = groupKey(tx(amount = "-100.00"))

        assertThat(result).contains("|E|")
    }

    @Test
    fun `should mark positive amount as income direction`() {
        val result = groupKey(tx(amount = "2000.00"))

        assertThat(result).contains("|I|")
    }

    @Test
    fun `should strip dates and numbers from purpose`() {
        val result = normalizePurpose("Miete 01.01.2024 ref 1234,56")

        assertThat(result).doesNotContain("2024", "1234")
        assertThat(result).contains("miete")
    }

    @Test
    fun `should normalize name to lowercase without special characters`() {
        val result = normalizeName("Max Müller GmbH & Co.")

        assertThat(result).doesNotContain("&", ".")
        assertThat(result).startsWith("max")
    }
}
