package com.moneylytics.api.adapter.input.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GenericCsvDetectorTest {
    private val detector = GenericCsvDetector()

    // ── Delimiter detection ──────────────────────────────────────────────────

    @Test
    fun `should detect semicolon as delimiter`() {
        val csv = "Datum;Betrag;IBAN\n15.01.2025;-100,00;DE00TEST\n16.01.2025;-50,00;DE00TEST"

        val result = detector.detect(csv)

        assertThat(result.result.delimiter).isEqualTo(";")
    }

    @Test
    fun `should detect comma as delimiter`() {
        val csv = "Date,Amount,IBAN\n2025-01-15,-100.00,DE00TEST\n2025-01-16,-50.00,DE00TEST"

        val result = detector.detect(csv)

        assertThat(result.result.delimiter).isEqualTo(",")
    }

    @Test
    fun `should return empty headers and sample rows when content is empty`() {
        val result = detector.detect("")

        assertThat(result.result.headers).isEmpty()
        assertThat(result.result.sampleRows).isEmpty()
        assertThat(result.fingerprint).isEmpty()
    }

    // ── computeFingerprint ───────────────────────────────────────────────────

    @Test
    fun `should sort headers and append delimiter in fingerprint`() {
        val fingerprint = detector.computeFingerprint(listOf("Betrag", "Datum", "IBAN"), ';')

        assertThat(fingerprint).isEqualTo("Betrag,Datum,IBAN|;")
    }

    @Test
    fun `should produce different fingerprints for different delimiters`() {
        val fp1 = detector.computeFingerprint(listOf("A", "B"), ',')
        val fp2 = detector.computeFingerprint(listOf("A", "B"), ';')

        assertThat(fp1).isNotEqualTo(fp2)
    }

    // ── Date format detection ────────────────────────────────────────────────

    @Test
    fun `should detect dd_MM_yyyy date format`() {
        val csv = "Datum;Betrag\n15.01.2025;-100\n20.02.2025;-50\n03.03.2025;-80"

        val result = detector.detect(csv)

        assertThat(result.result.detectedDateFormat).isEqualTo("dd.MM.yyyy")
    }

    @Test
    fun `should detect yyyy-MM-dd date format`() {
        val csv = "Date,Amount\n2025-01-15,-100\n2025-02-20,-50\n2025-03-03,-80"

        val result = detector.detect(csv)

        assertThat(result.result.detectedDateFormat).isEqualTo("yyyy-MM-dd")
    }

    @Test
    fun `should return null when no column looks like dates`() {
        val csv = "Name,Amount\nAlice,100\nBob,200"

        val result = detector.detect(csv)

        assertThat(result.result.detectedDateFormat).isNull()
    }

    // ── Amount format detection ──────────────────────────────────────────────

    @Test
    fun `should detect GERMAN amount format`() {
        val csv = "Datum;Betrag\n15.01.2025;-1.234,56\n20.02.2025;-86,11"

        val result = detector.detect(csv)

        assertThat(result.result.detectedAmountFormat).isEqualTo(AmountFormat.GERMAN)
    }

    @Test
    fun `should detect ENGLISH amount format`() {
        val csv = "Date,Amount\n2025-01-15,-1234.56\n2025-02-20,-86.11"

        val result = detector.detect(csv)

        assertThat(result.result.detectedAmountFormat).isEqualTo(AmountFormat.ENGLISH)
    }

    // ── Column suggestions ───────────────────────────────────────────────────

    @Test
    fun `should suggest Betrag header as amount column`() {
        val csv = "Datum;Betrag;IBAN\n15.01.2025;-100,00;DE00TEST"

        val result = detector.detect(csv)

        assertThat(result.result.suggestions.amount).isEqualTo("Betrag")
    }

    @Test
    fun `should suggest IBAN header as accountIban column`() {
        val csv = "Datum;Betrag;IBAN\n15.01.2025;-100,00;DE00TEST"

        val result = detector.detect(csv)

        assertThat(result.result.suggestions.accountIban).isEqualTo("IBAN")
    }

    @Test
    fun `should suggest column whose values look like dates`() {
        val csv = "Name;Buchungstag;Betrag\nTest;15.01.2025;-100,00"

        val result = detector.detect(csv)

        assertThat(result.result.suggestions.date).isEqualTo("Buchungstag")
    }

    // ── General detection ────────────────────────────────────────────────────

    @Test
    fun `should include headers in detection result`() {
        val csv = "Datum;Betrag;IBAN\n15.01.2025;-100,00;DE00TEST"

        val result = detector.detect(csv)

        assertThat(result.result.headers).containsExactly("Datum", "Betrag", "IBAN")
    }

    @Test
    fun `should include sample rows in detection result`() {
        val csv = "Datum;Betrag\n15.01.2025;-100,00\n16.01.2025;-50,00"

        val result = detector.detect(csv)

        assertThat(result.result.sampleRows).hasSize(2)
    }

    @Test
    fun `should return empty headers and sample when only header row present`() {
        val csv = "Datum;Betrag;IBAN"

        val result = detector.detect(csv)

        assertThat(result.result.headers).containsExactly("Datum", "Betrag", "IBAN")
        assertThat(result.result.sampleRows).isEmpty()
    }

    // ── splitLine (via detect) ───────────────────────────────────────────────

    @Test
    fun `should handle quoted fields containing the delimiter`() {
        val csv = """Datum;Betrag;Zweck
15.01.2025;-100,00;"Miete; Heizung"""

        val result = detector.detect(csv)

        assertThat(result.result.sampleRows[0]).hasSize(3)
        assertThat(result.result.sampleRows[0][2]).isEqualTo("Miete; Heizung")
    }
}
