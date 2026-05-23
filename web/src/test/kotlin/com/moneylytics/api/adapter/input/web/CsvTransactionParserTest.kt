package com.moneylytics.api.adapter.input.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class CsvTransactionParserTest {
    private val parser = CsvTransactionParser()

    // ── MLP Banking format (dd.MM.yyyy dates, German decimal amounts) ─────────

    @Test
    fun `should parse valid MLP Banking CSV and return all transactions`() {
        // Arrange
        val csvContent =
            javaClass.classLoader
                .getResourceAsStream("AccountSheet.csv")!!
                .bufferedReader()
                .readText()

        // Act
        val result = parser.parse(csvContent)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Valid::class.java)
        val valid = result as CsvParseResult.Valid
        assertThat(valid.transactions).hasSizeGreaterThan(0)
        val first = valid.transactions.first()
        assertThat(first.category).isEqualTo("Tools")
        assertThat(first.subcategory).isEqualTo("Sweepy")
        assertThat(first.bookingDate).isEqualTo(LocalDate.of(2025, 1, 2))
        assertThat(first.valueDate).isEqualTo(LocalDate.of(2025, 1, 2))
        assertThat(first.amount).isEqualByComparingTo(BigDecimal("-1.5"))
        assertThat(first.currency).isEqualTo("EUR")
        assertThat(first.accountIban).isEqualTo("DE33672300004016122250")
        assertThat(valid.accountNames).containsEntry("DE33672300004016122250", "KomfortKonto")
    }

    @Test
    fun `should collect all errors without stopping at the first one`() {
        // Arrange
        val csv =
            """
            Kategorie,Unterkategorie,Buchungstag,Valutadatum,Betrag,EUR,IBAN Auftragskonto
            Tools,Sweepy,not-a-date,also-not-a-date,not-a-number,EUR,DE33000000000000000000
            Auto,Benzin,03.01.2025,03.01.2025,bad-amount,EUR,DE33000000000000000000
            """.trimIndent()

        // Act
        val result = parser.parse(csv)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Invalid::class.java)
        val invalid = result as CsvParseResult.Invalid
        // Row 1: 3 errors (Buchungstag, Valutadatum, Betrag); Row 2: 1 error (Betrag)
        assertThat(invalid.errors).hasSize(4)
    }

    @Test
    fun `should return error for invalid Betrag format`() {
        // Arrange
        val csv =
            """
            Kategorie,Unterkategorie,Buchungstag,Valutadatum,Betrag,EUR,IBAN Auftragskonto
            Tools,Sweepy,02.01.2025,02.01.2025,abc,EUR,DE33000000000000000000
            """.trimIndent()

        // Act
        val result = parser.parse(csv)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Invalid::class.java)
        val invalid = result as CsvParseResult.Invalid
        assertThat(invalid.errors).hasSize(1)
        assertThat(invalid.errors[0].column).isEqualTo("Betrag")
        assertThat(invalid.errors[0].row).isEqualTo(2)
        assertThat(invalid.errors[0].value).isEqualTo("abc")
    }

    @Test
    fun `should return errors for invalid date formats`() {
        // Arrange
        val csv =
            """
            Kategorie,Unterkategorie,Buchungstag,Valutadatum,Betrag,EUR,IBAN Auftragskonto
            Tools,Sweepy,2025-01-02,2025-01-02,-1.5,EUR,DE33000000000000000000
            """.trimIndent()

        // Act
        val result = parser.parse(csv)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Invalid::class.java)
        val invalid = result as CsvParseResult.Invalid
        assertThat(invalid.errors.map { it.column })
            .containsExactlyInAnyOrder("Buchungstag", "Valutadatum")
    }

    @Test
    fun `should return error when a required column is missing`() {
        // Arrange — Valutadatum is absent; parser should name it in the message
        val csv =
            """
            Kategorie,Unterkategorie,Buchungstag,Betrag,EUR
            Tools,Sweepy,02.01.2025,-1.5,EUR
            """.trimIndent()

        // Act
        val result = parser.parse(csv)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Invalid::class.java)
        val invalid = result as CsvParseResult.Invalid
        assertThat(invalid.errors).hasSize(1)
        assertThat(invalid.errors[0].message).contains("Valutadatum")
    }

    @Test
    fun `should parse German decimal format correctly`() {
        // Arrange
        val csv =
            """
            Kategorie,Unterkategorie,Buchungstag,Valutadatum,Betrag,EUR,IBAN Auftragskonto
            Auto,Versicherung,02.01.2025,02.01.2025,"-1.234,56",EUR,DE33000000000000000000
            """.trimIndent()

        // Act
        val result = parser.parse(csv)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Valid::class.java)
        val valid = result as CsvParseResult.Valid
        assertThat(valid.transactions[0].amount).isEqualByComparingTo(BigDecimal("-1234.56"))
    }

    // ── Standard format (yyyy-MM-dd dates, English column names) ─────────────

    @Test
    fun `should auto-detect and parse Standard format CSV`() {
        // Arrange
        val csvContent =
            javaClass.classLoader
                .getResourceAsStream("AccountSheet2.csv")!!
                .bufferedReader()
                .readText()

        // Act
        val result = parser.parse(csvContent)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Valid::class.java)
        val valid = result as CsvParseResult.Valid
        // First row: ,,Bargeld,...,2025-08-01,2025-08-01,...,-400,EUR
        val first = valid.transactions.first()
        assertThat(first.bookingDate).isEqualTo(LocalDate.of(2025, 8, 1))
        assertThat(first.valueDate).isEqualTo(LocalDate.of(2025, 8, 1))
        assertThat(first.amount).isEqualByComparingTo(BigDecimal("-400"))
        assertThat(first.currency).isEqualTo("EUR")
        assertThat(first.accountIban).isEqualTo("Hauptkonto")
    }

    @Test
    fun `should parse Standard format amount with comma decimal separator`() {
        // Arrange — "0,01" is how the STANDARD format encodes 0.01 EUR
        val csv =
            """
            Main category,Second Category,booking_date,valuta_date,amount,currency,account_type
            Einkäufe,PayPal,2025-08-04,2025-08-04,"0,01",EUR,Hauptkonto
            Einkäufe,Amazon,2025-08-08,2025-08-08,"-86,11",EUR,Hauptkonto
            """.trimIndent()

        // Act
        val result = parser.parse(csv)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Valid::class.java)
        val valid = result as CsvParseResult.Valid
        assertThat(valid.transactions[0].amount).isEqualByComparingTo(BigDecimal("0.01"))
        assertThat(valid.transactions[1].amount).isEqualByComparingTo(BigDecimal("-86.11"))
    }

    @Test
    fun `should parse Standard format category and subcategory fields`() {
        // Arrange
        val csv =
            """
            Main category,Second Category,booking_date,valuta_date,amount,currency,account_type
            Unterhaltung,Netflix und Spotify,2025-08-04,2025-08-04,-20,EUR,Hauptkonto
            Miete,Wohnung+Garage,2025-08-04,2025-08-04,-460,EUR,Hauptkonto
            """.trimIndent()

        // Act
        val result = parser.parse(csv)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Valid::class.java)
        val valid = result as CsvParseResult.Valid
        assertThat(valid.transactions[0].category).isEqualTo("Unterhaltung")
        assertThat(valid.transactions[0].subcategory).isEqualTo("Netflix und Spotify")
        assertThat(valid.transactions[1].category).isEqualTo("Miete")
        assertThat(valid.transactions[1].subcategory).isEqualTo("Wohnung+Garage")
    }

    @Test
    fun `should return error for completely unrecognized CSV format`() {
        // Arrange — none of the known formats can match these headers
        val csv =
            """
            foo,bar,baz
            1,2,3
            """.trimIndent()

        // Act
        val result = parser.parse(csv)

        // Assert
        assertThat(result).isInstanceOf(CsvParseResult.Invalid::class.java)
    }
}
