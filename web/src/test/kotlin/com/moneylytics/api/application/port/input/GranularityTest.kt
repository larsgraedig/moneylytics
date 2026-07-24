package com.moneylytics.api.application.port.input

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GranularityTest {
    // ── generateBuckets ──────────────────────────────────────────────────────

    @Test
    fun `should generate monthly buckets for multi-month range`() {
        val buckets = generateBuckets(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31), Granularity.MONTHLY)

        assertThat(buckets).containsExactly("2025-01", "2025-02", "2025-03")
    }

    @Test
    fun `should generate single monthly bucket for single-month range`() {
        val buckets = generateBuckets(LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 25), Granularity.MONTHLY)

        assertThat(buckets).containsExactly("2025-03")
    }

    @Test
    fun `should generate weekly buckets starting from Monday of from-date`() {
        // Jan 6 2025 is a Monday (ISO week 2025-W02)
        val from = LocalDate.of(2025, 1, 6)
        val to = LocalDate.of(2025, 1, 12)

        val buckets = generateBuckets(from, to, Granularity.WEEKLY)

        assertThat(buckets).containsExactly("2025-W02")
    }

    @Test
    fun `should start weekly buckets from preceding Monday when from is mid-week`() {
        // Jan 8 2025 is a Wednesday → preceding Monday is Jan 6 (W02). Jan 13 (W03) also ≤ Jan 14.
        val from = LocalDate.of(2025, 1, 8)
        val to = LocalDate.of(2025, 1, 14)

        val buckets = generateBuckets(from, to, Granularity.WEEKLY)

        assertThat(buckets).containsExactly("2025-W02", "2025-W03")
    }

    @Test
    fun `should generate daily buckets for each day in range`() {
        val buckets = generateBuckets(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3), Granularity.DAILY)

        assertThat(buckets).containsExactly("2025-01-01", "2025-01-02", "2025-01-03")
    }

    @Test
    fun `should generate quarterly buckets for full-year range`() {
        val buckets = generateBuckets(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 9, 30), Granularity.QUARTERLY)

        assertThat(buckets).containsExactly("2025-Q1", "2025-Q2", "2025-Q3")
    }

    @Test
    fun `should align quarterly buckets to quarter start when from is mid-quarter`() {
        val from = LocalDate.of(2025, 4, 15)
        val to = LocalDate.of(2025, 6, 30)

        val buckets = generateBuckets(from, to, Granularity.QUARTERLY)

        assertThat(buckets).containsExactly("2025-Q2")
    }

    @Test
    fun `should generate yearly buckets for multi-year range`() {
        val buckets = generateBuckets(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31), Granularity.YEARLY)

        assertThat(buckets).containsExactly("2024", "2025", "2026")
    }

    @Test
    fun `should generate single yearly bucket for same-year range`() {
        val buckets = generateBuckets(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 11, 30), Granularity.YEARLY)

        assertThat(buckets).containsExactly("2025")
    }

    @Test
    fun `should generate bi-yearly buckets for full-year range`() {
        val buckets = generateBuckets(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), Granularity.BI_YEARLY)

        assertThat(buckets).containsExactly("2025-H1", "2025-H2")
    }

    @Test
    fun `should start bi-yearly at H2 when from is in second half`() {
        val buckets = generateBuckets(LocalDate.of(2025, 7, 1), LocalDate.of(2025, 12, 31), Granularity.BI_YEARLY)

        assertThat(buckets).containsExactly("2025-H2")
    }

    // ── bucketKey ────────────────────────────────────────────────────────────

    @Test
    fun `should return year-month string for MONTHLY granularity`() {
        assertThat(bucketKey(LocalDate.of(2025, 3, 15), Granularity.MONTHLY)).isEqualTo("2025-03")
    }

    @Test
    fun `should return ISO week key for WEEKLY granularity`() {
        // Jan 8 2025 is a Wednesday → Monday Jan 6 → week 2025-W02
        assertThat(bucketKey(LocalDate.of(2025, 1, 8), Granularity.WEEKLY)).isEqualTo("2025-W02")
    }

    @Test
    fun `should return ISO date string for DAILY granularity`() {
        assertThat(bucketKey(LocalDate.of(2025, 1, 15), Granularity.DAILY)).isEqualTo("2025-01-15")
    }

    @Test
    fun `should return quarter key Q1 for January`() {
        assertThat(bucketKey(LocalDate.of(2025, 1, 15), Granularity.QUARTERLY)).isEqualTo("2025-Q1")
    }

    @Test
    fun `should return quarter key Q2 for April`() {
        assertThat(bucketKey(LocalDate.of(2025, 4, 1), Granularity.QUARTERLY)).isEqualTo("2025-Q2")
    }

    @Test
    fun `should return quarter key Q4 for October`() {
        assertThat(bucketKey(LocalDate.of(2025, 10, 1), Granularity.QUARTERLY)).isEqualTo("2025-Q4")
    }

    @Test
    fun `should return year string for YEARLY granularity`() {
        assertThat(bucketKey(LocalDate.of(2025, 6, 15), Granularity.YEARLY)).isEqualTo("2025")
    }

    @Test
    fun `should return H1 for dates in first half of year`() {
        assertThat(bucketKey(LocalDate.of(2025, 1, 1), Granularity.BI_YEARLY)).isEqualTo("2025-H1")
        assertThat(bucketKey(LocalDate.of(2025, 6, 30), Granularity.BI_YEARLY)).isEqualTo("2025-H1")
    }

    @Test
    fun `should return H2 for dates in second half of year`() {
        assertThat(bucketKey(LocalDate.of(2025, 7, 1), Granularity.BI_YEARLY)).isEqualTo("2025-H2")
        assertThat(bucketKey(LocalDate.of(2025, 12, 31), Granularity.BI_YEARLY)).isEqualTo("2025-H2")
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `should use ISO week-based year for week spanning year boundary`() {
        // Dec 31 2024 is Tuesday. ISO week: Monday of that week = Dec 30, 2024.
        // That week's Thursday is Jan 2 2025 → ISO week 2025-W01.
        assertThat(bucketKey(LocalDate.of(2024, 12, 31), Granularity.WEEKLY)).isEqualTo("2025-W01")
    }

    @Test
    fun `should format week number with leading zero`() {
        // Jan 6 2025 is week 2 → formatted as W02, not W2
        assertThat(bucketKey(LocalDate.of(2025, 1, 6), Granularity.WEEKLY)).isEqualTo("2025-W02")
    }
}
