package com.moneylytics.api.adapter.input.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionFingerprintTest {
    private val date = LocalDate.of(2025, 1, 15)

    @Test
    fun `should assign unique fingerprints to distinct rows`() {
        val rows =
            listOf(
                row(1, BigDecimal("-100.00")),
                row(2, BigDecimal("-200.00")),
            )

        val result = assignFingerprints(rows)

        assertThat(result[1]).isNotEqualTo(result[2])
    }

    @Test
    fun `should assign different fingerprints to duplicate rows`() {
        val r = row(1, BigDecimal("-100.00"))
        val rows = listOf(r, r.copy(rowNumber = 2))

        val result = assignFingerprints(rows)

        assertThat(result[1]).isNotEqualTo(result[2])
    }

    @Test
    fun `should map row number to fingerprint`() {
        val rows = listOf(row(7, BigDecimal("-50.00")))

        val result = assignFingerprints(rows)

        assertThat(result).containsKey(7)
        assertThat(result[7]).isNotBlank()
    }

    @Test
    fun `should produce deterministic fingerprints for same input`() {
        val rows = listOf(row(1, BigDecimal("-100.00")))

        val fp1 = assignFingerprints(rows)[1]
        val fp2 = assignFingerprints(rows)[1]

        assertThat(fp1).isEqualTo(fp2)
    }

    @Test
    fun `sha256 should return 64-character hex string`() {
        val result = sha256("test input")

        assertThat(result).hasSize(64)
        assertThat(result).matches("[0-9a-f]+")
    }

    private fun row(
        rowNumber: Int,
        amount: BigDecimal,
    ) = ParsedRawRow(
        rowNumber = rowNumber,
        bookingDate = date,
        valueDate = date,
        bookingDateRaw = date.toString(),
        valueDateRaw = date.toString(),
        counterparty = "",
        counterpartyIban = null,
        purpose = "",
        amount = amount,
        amountRaw = amount.toPlainString(),
        currency = "EUR",
        accountIban = "DE00TEST",
        accountName = "Test",
        errors = emptyList(),
    )
}
