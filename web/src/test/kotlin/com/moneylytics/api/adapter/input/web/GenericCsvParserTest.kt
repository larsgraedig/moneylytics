package com.moneylytics.api.adapter.input.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class GenericCsvParserTest {
    private val parser = GenericCsvParser()

    private val basicMapping =
        GenericCsvMapping(
            delimiter = ";",
            dateColumn = "Datum",
            dateFormat = "dd.MM.yyyy",
            amountColumn = "Betrag",
            amountFormat = AmountFormat.GERMAN,
            purposeColumn = null,
            categoryColumn = null,
            subcategoryColumn = null,
            accountIbanColumn = null,
            currencyColumn = null,
            fixedAccountIban = "DE00TEST",
            fixedCurrency = "EUR",
        )

    // ── parse: basic happy path ──────────────────────────────────────────────

    @Test
    fun `should return empty list when content is empty`() {
        val (transactions, accounts) = parser.parse("", basicMapping)

        assertThat(transactions).isEmpty()
        assertThat(accounts).isEmpty()
    }

    @Test
    fun `should return empty list when date column is not in headers`() {
        val content = "Falsch;Betrag\n15.01.2025;-100,00"
        val (transactions, _) = parser.parse(content, basicMapping)

        assertThat(transactions).isEmpty()
    }

    @Test
    fun `should return empty list when amount column is not in headers`() {
        val content = "Datum;Falsch\n15.01.2025;-100,00"
        val (transactions, _) = parser.parse(content, basicMapping)

        assertThat(transactions).isEmpty()
    }

    @Test
    fun `should parse a simple valid row`() {
        val content = "Datum;Betrag\n15.01.2025;-950,00"
        val (transactions, _) = parser.parse(content, basicMapping)

        assertThat(transactions).hasSize(1)
        assertThat(transactions[0].bookingDate).isEqualTo(LocalDate.of(2025, 1, 15))
        assertThat(transactions[0].amount).isEqualByComparingTo(BigDecimal("-950.00"))
        assertThat(transactions[0].accountIban).isEqualTo("DE00TEST")
        assertThat(transactions[0].currency).isEqualTo("EUR")
    }

    // ── parse: skip invalid rows ─────────────────────────────────────────────

    @Test
    fun `should skip rows with invalid dates`() {
        val content = "Datum;Betrag\nnot-a-date;-100,00\n15.01.2025;-50,00"
        val (transactions, _) = parser.parse(content, basicMapping)

        assertThat(transactions).hasSize(1)
        assertThat(transactions[0].amount).isEqualByComparingTo(BigDecimal("-50.00"))
    }

    @Test
    fun `should skip rows with invalid amounts`() {
        val content = "Datum;Betrag\n15.01.2025;not-a-number\n16.01.2025;-50,00"
        val (transactions, _) = parser.parse(content, basicMapping)

        assertThat(transactions).hasSize(1)
    }

    @Test
    fun `should skip rows with blank amounts`() {
        val content = "Datum;Betrag\n15.01.2025;\n16.01.2025;-50,00"
        val (transactions, _) = parser.parse(content, basicMapping)

        assertThat(transactions).hasSize(1)
    }

    // ── parse: amount formats ────────────────────────────────────────────────

    @Test
    fun `should parse German amount format with comma decimal and dot thousands`() {
        val content = "Datum;Betrag\n15.01.2025;\"-1.234,56\""
        val (transactions, _) = parser.parse(content, basicMapping)

        assertThat(transactions[0].amount).isEqualByComparingTo(BigDecimal("-1234.56"))
    }

    @Test
    fun `should parse German small amount without thousands separator`() {
        val content = "Datum;Betrag\n15.01.2025;-86,11"
        val (transactions, _) = parser.parse(content, basicMapping)

        assertThat(transactions[0].amount).isEqualByComparingTo(BigDecimal("-86.11"))
    }

    @Test
    fun `should parse English amount format with dot decimal`() {
        val content = "Date;Amount\n2025-01-15;-1234.56"
        val mapping =
            basicMapping.copy(
                delimiter = ";",
                dateColumn = "Date",
                dateFormat = "yyyy-MM-dd",
                amountColumn = "Amount",
                amountFormat = AmountFormat.ENGLISH,
            )

        val (transactions, _) = parser.parse(content, mapping)

        assertThat(transactions[0].amount).isEqualByComparingTo(BigDecimal("-1234.56"))
    }

    @Test
    fun `should parse English amount with comma thousands separator`() {
        val content = "Date;Amount\n2025-01-15;-1,234.56"
        val mapping =
            basicMapping.copy(
                dateColumn = "Date",
                dateFormat = "yyyy-MM-dd",
                amountColumn = "Amount",
                amountFormat = AmountFormat.ENGLISH,
            )

        val (transactions, _) = parser.parse(content, mapping)

        assertThat(transactions[0].amount).isEqualByComparingTo(BigDecimal("-1234.56"))
    }

    // ── parse: IBAN and currency resolution ──────────────────────────────────

    @Test
    fun `should use fixedAccountIban when no IBAN column in mapping`() {
        val content = "Datum;Betrag\n15.01.2025;-100,00"
        val (transactions, accounts) = parser.parse(content, basicMapping.copy(fixedAccountIban = "DE99FIXED"))

        assertThat(transactions[0].accountIban).isEqualTo("DE99FIXED")
        assertThat(accounts).containsKey("DE99FIXED")
    }

    @Test
    fun `should read IBAN from column when accountIbanColumn is set`() {
        val content = "Datum;Betrag;IBAN\n15.01.2025;-100,00;DE12345678"
        val mapping = basicMapping.copy(accountIbanColumn = "IBAN")

        val (transactions, _) = parser.parse(content, mapping)

        assertThat(transactions[0].accountIban).isEqualTo("DE12345678")
    }

    @Test
    fun `should fall back to IMPORTED when IBAN column is blank and no fixedAccountIban`() {
        val content = "Datum;Betrag;IBAN\n15.01.2025;-100,00;"
        val mapping = basicMapping.copy(accountIbanColumn = "IBAN", fixedAccountIban = null)

        val (transactions, _) = parser.parse(content, mapping)

        assertThat(transactions[0].accountIban).isEqualTo("IMPORTED")
    }

    @Test
    fun `should use fixedCurrency when no currency column`() {
        val content = "Datum;Betrag\n15.01.2025;-100,00"
        val (transactions, _) = parser.parse(content, basicMapping.copy(fixedCurrency = "USD"))

        assertThat(transactions[0].currency).isEqualTo("USD")
    }

    // ── parse: optional field mapping ────────────────────────────────────────

    @Test
    fun `should map purpose from optional column`() {
        val content = "Datum;Betrag;Zweck\n15.01.2025;-950,00;Miete Januar"
        val mapping = basicMapping.copy(purposeColumn = "Zweck")

        val (transactions, _) = parser.parse(content, mapping)

        assertThat(transactions[0].purpose).isEqualTo("Miete Januar")
    }

    @Test
    fun `should set purpose to null when purpose column value is blank`() {
        val content = "Datum;Betrag;Zweck\n15.01.2025;-950,00;"
        val mapping = basicMapping.copy(purposeColumn = "Zweck")

        val (transactions, _) = parser.parse(content, mapping)

        assertThat(transactions[0].purpose).isNull()
    }

    @Test
    fun `should default category and subcategory to Sonstiges when blank`() {
        val content = "Datum;Betrag;Kat;UnterKat\n15.01.2025;-100,00;;"
        val mapping = basicMapping.copy(categoryColumn = "Kat", subcategoryColumn = "UnterKat")

        val (transactions, _) = parser.parse(content, mapping)

        assertThat(transactions[0].category).isEqualTo("Sonstiges")
        assertThat(transactions[0].subcategory).isEqualTo("Sonstiges")
    }

    @Test
    fun `should map category and subcategory when present`() {
        val content = "Datum;Betrag;Kat;UnterKat\n15.01.2025;-100,00;Lebensmittel;Supermarkt"
        val mapping = basicMapping.copy(categoryColumn = "Kat", subcategoryColumn = "UnterKat")

        val (transactions, _) = parser.parse(content, mapping)

        assertThat(transactions[0].category).isEqualTo("Lebensmittel")
        assertThat(transactions[0].subcategory).isEqualTo("Supermarkt")
    }

    @Test
    fun `should map counterpartyName and counterpartyIban from optional columns`() {
        val content = "Datum;Betrag;Gläubiger;GläubigerIBAN\n15.01.2025;-950,00;Landlord GmbH;DE99LAND"
        val mapping = basicMapping.copy(counterpartyNameColumn = "Gläubiger", counterpartyIbanColumn = "GläubigerIBAN")

        val (transactions, _) = parser.parse(content, mapping)

        assertThat(transactions[0].counterpartyName).isEqualTo("Landlord GmbH")
        assertThat(transactions[0].counterpartyIban).isEqualTo("DE99LAND")
    }

    // ── parse: accountNames map ──────────────────────────────────────────────

    @Test
    fun `should collect unique IBANs in accountNames map`() {
        val content = "Datum;Betrag;IBAN\n15.01.2025;-100,00;DE01\n16.01.2025;-50,00;DE02\n17.01.2025;-30,00;DE01"
        val mapping = basicMapping.copy(accountIbanColumn = "IBAN")

        val (_, accounts) = parser.parse(content, mapping)

        assertThat(accounts.keys).containsExactlyInAnyOrder("DE01", "DE02")
    }

    // ── preview ──────────────────────────────────────────────────────────────

    @Test
    fun `should return empty list when preview content is empty`() {
        val rows = parser.preview("", basicMapping)

        assertThat(rows).isEmpty()
    }

    @Test
    fun `should return preview rows with correct data`() {
        val content = "Datum;Betrag\n15.01.2025;-950,00"

        val rows = parser.preview(content, basicMapping)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].date).isEqualTo("2025-01-15")
        assertThat(rows[0].amount).isEqualTo(-950.0)
        assertThat(rows[0].currency).isEqualTo("EUR")
        assertThat(rows[0].accountIban).isEqualTo("DE00TEST")
    }

    @Test
    fun `should skip invalid rows in preview`() {
        val content = "Datum;Betrag\nnot-a-date;-100,00\n15.01.2025;-50,00"

        val rows = parser.preview(content, basicMapping)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].amount).isEqualTo(-50.0)
    }

    @Test
    fun `should assign different fingerprints to duplicate rows in preview`() {
        val content = "Datum;Betrag\n15.01.2025;-100,00\n15.01.2025;-100,00"

        val rows = parser.preview(content, basicMapping)

        assertThat(rows).hasSize(2)
        assertThat(rows[0].fingerprint).isNotEqualTo(rows[1].fingerprint)
    }
}
