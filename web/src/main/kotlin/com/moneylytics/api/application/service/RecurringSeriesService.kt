package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.ConfirmRecurringSeriesCommand
import com.moneylytics.api.application.port.input.ConfirmRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeCommand
import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeUseCase
import com.moneylytics.api.application.port.input.CreateRecurringSeriesCommand
import com.moneylytics.api.application.port.input.CreateRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.DeleteRecurringSeriesCommand
import com.moneylytics.api.application.port.input.DeleteRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.DetectRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetRecurringSeriesQuery
import com.moneylytics.api.application.port.input.GetRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.application.port.output.RecurringFalsePositiveRepository
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.RecurringTypeClassifier
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDeviation
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringFalsePositive
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import com.moneylytics.api.domain.toFeatures
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

@Service
class RecurringSeriesService(
    private val transactionRepository: TransactionRepository,
    private val recurringSeriesRepository: RecurringSeriesRepository,
    private val falsePositiveRepository: RecurringFalsePositiveRepository,
    private val detector: RecurringSeriesDetector,
    private val classifier: RecurringTypeClassifier,
) : DetectRecurringSeriesUseCase,
    GetRecurringSeriesUseCase,
    CorrectRecurringSeriesTypeUseCase,
    ConfirmRecurringSeriesUseCase,
    CreateRecurringSeriesUseCase,
    DeleteRecurringSeriesUseCase {
    private companion object {
        const val MIN_GRACE_DAYS = 3
        const val GRACE_PERIOD_FACTOR = 0.15
        const val MIN_OCCURRENCES_FOR_DEVIATION = 3
        val AMOUNT_CHANGE_THRESHOLD: BigDecimal = BigDecimal("0.15")
        const val AMOUNT_CHANGE_SCALE = 4
        const val DATE_SHIFT_MIN_DAYS = 5.0
        const val DATE_SHIFT_FACTOR = 0.25

        val CADENCE_INTERVAL_DAYS: Map<RecurrenceCadence, Int> =
            mapOf(
                RecurrenceCadence.WEEKLY to 7,
                RecurrenceCadence.MONTHLY to 30,
                RecurrenceCadence.QUARTERLY to 91,
                RecurrenceCadence.SEMIANNUAL to 182,
                RecurrenceCadence.YEARLY to 365,
            )
    }

    override fun detect(command: RefreshRecurringSeriesCommand): List<RecurringSeries> {
        val today = LocalDate.now()
        val from = today.minusMonths(command.lookbackMonths)
        val transactions = transactionRepository.findByAccountingDateBetween(from, today, command.userId)

        classifier.seedIfEmpty(command.userId)

        val falsePositiveFingerprints = falsePositiveRepository.findFingerprintsByUserId(command.userId)

        return detector
            .detect(transactions)
            .map { s -> s.copy(type = classifier.classify(command.userId, s.toFeatures())) }
            .filter { s -> !(s.amountVariable && s.type == RecurringType.OTHER) }
            .map { s -> s.copy(isFalsePositive = s.fingerprint in falsePositiveFingerprints) }
            .map { s -> computeDeviation(s, today) }
    }

    override fun confirm(command: ConfirmRecurringSeriesCommand): List<RecurringSeries> {
        val today = LocalDate.now()
        val from = today.minusMonths(command.lookbackMonths)
        val transactions = transactionRepository.findByAccountingDateBetween(from, today, command.userId)

        classifier.seedIfEmpty(command.userId)

        val confirmedSet = command.confirmedFingerprints.toSet()

        val confirmed =
            detector
                .detect(transactions)
                .map { s -> s.copy(type = classifier.classify(command.userId, s.toFeatures())) }
                .filter { s -> !(s.amountVariable && s.type == RecurringType.OTHER) }
                .filter { s -> s.fingerprint in confirmedSet }

        recurringSeriesRepository.replaceAllForUser(confirmed, command.userId)

        if (command.falsePositiveFingerprints.isNotEmpty()) {
            val existing = falsePositiveRepository.findFingerprintsByUserId(command.userId)
            val newEntries =
                command.falsePositiveFingerprints
                    .filter { it !in existing }
                    .map { RecurringFalsePositive(userId = command.userId, fingerprint = it, createdAt = today) }
            if (newEntries.isNotEmpty()) falsePositiveRepository.saveAll(newEntries)
        }

        // If a previously false-positive series is now confirmed, remove it from the false positive list.
        val toRemove = command.confirmedFingerprints.filter { it in falsePositiveRepository.findFingerprintsByUserId(command.userId) }
        if (toRemove.isNotEmpty()) falsePositiveRepository.deleteByUserIdAndFingerprints(command.userId, toRemove)

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

    override fun create(command: CreateRecurringSeriesCommand): RecurringSeries {
        val intervalDays = checkNotNull(CADENCE_INTERVAL_DAYS[command.cadence])
        val lastSeen = command.lastBookingDate ?: LocalDate.now()
        val series =
            RecurringSeries(
                label = command.label,
                type = command.type,
                direction = command.direction,
                cadence = command.cadence,
                intervalDays = intervalDays,
                expectedAmount = command.expectedAmount,
                amountVariable = false,
                currency = command.currency,
                accountIban = command.accountIban,
                firstSeen = lastSeen,
                lastSeen = lastSeen,
                occurrenceCount = 0,
                nextExpectedDate = lastSeen.plusDays(intervalDays.toLong()),
                status = RecurrenceStatus.MANUAL,
                fingerprint = "manual:${UUID.randomUUID()}",
            )
        return recurringSeriesRepository.save(series, command.userId)
    }

    override fun delete(command: DeleteRecurringSeriesCommand) {
        recurringSeriesRepository.deleteByIdAndUserId(command.seriesId, command.userId)
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
        val grace = maxOf(MIN_GRACE_DAYS, (series.intervalDays * GRACE_PERIOD_FACTOR).toInt())
        val sortedOccurrences = series.occurrences.sortedByDescending { it.date }
        val lastOccurrence = sortedOccurrences.firstOrNull()
        val penultimate = sortedOccurrences.getOrNull(1)

        val deviation =
            when {
                today > series.nextExpectedDate.plusDays(grace.toLong()) -> RecurrenceDeviation.OVERDUE
                lastOccurrence != null && series.occurrenceCount >= MIN_OCCURRENCES_FOR_DEVIATION -> {
                    val lastAmount = lastOccurrence.amount.abs()
                    val expected = series.expectedAmount.abs()
                    val amountChanged =
                        expected > BigDecimal.ZERO &&
                            (lastAmount - expected)
                                .abs()
                                .divide(expected, AMOUNT_CHANGE_SCALE, RoundingMode.HALF_UP) > AMOUNT_CHANGE_THRESHOLD

                    val dateShifted =
                        penultimate != null &&
                            run {
                                val lastInterval = ChronoUnit.DAYS.between(penultimate.date, lastOccurrence.date).toInt()
                                abs(lastInterval - series.intervalDays).toDouble() >
                                    maxOf(DATE_SHIFT_MIN_DAYS, series.intervalDays * DATE_SHIFT_FACTOR)
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
