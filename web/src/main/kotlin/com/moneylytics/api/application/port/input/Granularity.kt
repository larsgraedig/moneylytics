package com.moneylytics.api.application.port.input

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields

enum class Granularity {
    MONTHLY,
    WEEKLY,
    DAILY,
    QUARTERLY,
    YEARLY,
    BI_YEARLY,
    ;

    companion object {
        internal const val MONTHS_PER_QUARTER = 3
        internal const val MONTHS_PER_HALF_YEAR = 6
        internal const val SECOND_HALF_START_MONTH = 7
    }
}

fun generateBuckets(
    from: LocalDate,
    to: LocalDate,
    granularity: Granularity,
): List<String> =
    when (granularity) {
        Granularity.MONTHLY ->
            generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(YearMonth.from(to)) }
                .map { it.toString() }
                .toList()

        Granularity.WEEKLY ->
            generateSequence(from.with(DayOfWeek.MONDAY)) { it.plusWeeks(1) }
                .takeWhile { !it.isAfter(to) }
                .map { weekKey(it) }
                .toList()

        Granularity.DAILY ->
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .map { it.toString() }
                .toList()

        Granularity.QUARTERLY -> {
            val startOfQuarter =
                from
                    .withMonth(
                        ((from.monthValue - 1) / Granularity.MONTHS_PER_QUARTER) * Granularity.MONTHS_PER_QUARTER + 1,
                    ).withDayOfMonth(1)
            generateSequence(startOfQuarter) { it.plusMonths(Granularity.MONTHS_PER_QUARTER.toLong()) }
                .takeWhile { !it.isAfter(to) }
                .map { quarterKey(it) }
                .toList()
        }

        Granularity.YEARLY -> (from.year..to.year).map { it.toString() }

        Granularity.BI_YEARLY -> {
            val startMonth = if (from.monthValue <= Granularity.MONTHS_PER_HALF_YEAR) 1 else Granularity.SECOND_HALF_START_MONTH
            val startDate = from.withMonth(startMonth).withDayOfMonth(1)
            generateSequence(startDate) { it.plusMonths(Granularity.MONTHS_PER_HALF_YEAR.toLong()) }
                .takeWhile { !it.isAfter(to) }
                .map { halfYearKey(it) }
                .toList()
        }
    }

fun bucketKey(
    date: LocalDate,
    granularity: Granularity,
): String =
    when (granularity) {
        Granularity.MONTHLY -> YearMonth.from(date).toString()
        Granularity.WEEKLY -> weekKey(date.with(DayOfWeek.MONDAY))
        Granularity.DAILY -> date.toString()
        Granularity.QUARTERLY -> quarterKey(date)
        Granularity.YEARLY -> date.year.toString()
        Granularity.BI_YEARLY -> halfYearKey(date)
    }

private fun weekKey(monday: LocalDate): String =
    "${monday.get(IsoFields.WEEK_BASED_YEAR)}-W${String.format("%02d", monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))}"

private fun quarterKey(date: LocalDate): String {
    val quarter = (date.monthValue - 1) / Granularity.MONTHS_PER_QUARTER + 1
    return "${date.year}-Q$quarter"
}

private fun halfYearKey(date: LocalDate): String {
    val half = if (date.monthValue <= Granularity.MONTHS_PER_HALF_YEAR) 1 else 2
    return "${date.year}-H$half"
}
