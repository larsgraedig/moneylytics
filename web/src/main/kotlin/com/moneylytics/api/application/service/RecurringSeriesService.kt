package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeCommand
import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeUseCase
import com.moneylytics.api.application.port.input.DetectRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetRecurringSeriesQuery
import com.moneylytics.api.application.port.input.GetRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.RecurringTypeClassifier
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.RecurrenceDeviation
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import com.moneylytics.api.domain.toFeatures
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Service
class RecurringSeriesService(
    private val transactionRepository: TransactionRepository,
    private val recurringSeriesRepository: RecurringSeriesRepository,
    private val detector: RecurringSeriesDetector,
    private val classifier: RecurringTypeClassifier,
) : DetectRecurringSeriesUseCase,
    GetRecurringSeriesUseCase,
    CorrectRecurringSeriesTypeUseCase {
    override fun detect(command: RefreshRecurringSeriesCommand): List<RecurringSeries> {
        val today = LocalDate.now()
        val from = today.minusMonths(command.lookbackMonths)
        val transactions = transactionRepository.findByAccountingDateBetween(from, today, command.userId)

        classifier.seedIfEmpty(command.userId)

        val classified =
            detector
                .detect(transactions)
                .map { s -> s.copy(type = classifier.classify(command.userId, s.toFeatures())) }
                .filter { s -> !(s.amountVariable && s.type == RecurringType.OTHER) }

        recurringSeriesRepository.replaceAllForUser(classified, command.userId)
        return recurringSeriesRepository.findByUserId(command.userId).map { computeDeviation(it, today) }
    }

    override fun getRecurringSeries(query: GetRecurringSeriesQuery): List<RecurringSeries> {
        val today = LocalDate.now()
        return recurringSeriesRepository
            .findByUserId(query.userId)
            .let { list -> query.direction?.let { d -> list.filter { it.direction == d } } ?: list }
            .let { list -> query.type?.let { t -> list.filter { it.type == t } } ?: list }
            .map { computeDeviation(it, today) }
    }

    override fun correctType(command: CorrectRecurringSeriesTypeCommand) {
        val series =
            recurringSeriesRepository.findByUserId(command.userId).find { it.id == command.seriesId }
                ?: throw NoSuchElementException("Series ${command.seriesId} not found for user ${command.userId}")
        recurringSeriesRepository.updateType(command.seriesId, command.type)
        classifier.train(command.userId, command.type, series.toFeatures())
    }

    private fun computeDeviation(
        series: RecurringSeries,
        today: LocalDate,
    ): RecurringSeries {
        val grace = maxOf(3, (series.intervalDays * 0.15).toInt())
        val sortedOccurrences = series.occurrences.sortedByDescending { it.date }
        val lastOccurrence = sortedOccurrences.firstOrNull()
        val penultimate = sortedOccurrences.getOrNull(1)

        val deviation =
            when {
                today > series.nextExpectedDate.plusDays(grace.toLong()) -> RecurrenceDeviation.OVERDUE
                lastOccurrence != null && series.occurrenceCount >= 3 -> {
                    val lastAmount = lastOccurrence.amount.abs()
                    val expected = series.expectedAmount.abs()
                    val amountChanged =
                        expected > BigDecimal.ZERO &&
                            (lastAmount - expected)
                                .abs()
                                .divide(expected, 4, RoundingMode.HALF_UP) > BigDecimal("0.15")

                    val dateShifted =
                        penultimate != null &&
                            run {
                                val lastInterval = ChronoUnit.DAYS.between(penultimate.date, lastOccurrence.date).toInt()
                                abs(lastInterval - series.intervalDays).toDouble() > maxOf(5.0, series.intervalDays * 0.25)
                            }

                    when {
                        amountChanged -> RecurrenceDeviation.AMOUNT_CHANGED
                        dateShifted -> RecurrenceDeviation.DATE_SHIFTED
                        else -> RecurrenceDeviation.ON_TRACK
                    }
                }
                else -> RecurrenceDeviation.ON_TRACK
            }

        return series.copy(deviation = deviation)
    }
}
