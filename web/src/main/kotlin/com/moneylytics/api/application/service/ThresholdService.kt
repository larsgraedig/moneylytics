package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.DeleteThresholdUseCase
import com.moneylytics.api.application.port.input.GetThresholdStatusQuery
import com.moneylytics.api.application.port.input.GetThresholdStatusUseCase
import com.moneylytics.api.application.port.input.GetThresholdsUseCase
import com.moneylytics.api.application.port.input.SaveThresholdUseCase
import com.moneylytics.api.application.port.input.ThresholdStatusItem
import com.moneylytics.api.application.port.input.ThresholdStatusResponse
import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import com.moneylytics.api.domain.ThresholdStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ln

@Service
class ThresholdService(
    private val thresholdRepository: ThresholdRepository,
    private val transactionRepository: TransactionRepository,
) : GetThresholdsUseCase,
    SaveThresholdUseCase,
    DeleteThresholdUseCase,
    GetThresholdStatusUseCase {
    override fun getThresholds(userId: Long): List<Threshold> = thresholdRepository.findAllByUserId(userId)

    override fun saveThreshold(
        threshold: Threshold,
        userId: Long,
    ): Threshold = thresholdRepository.upsert(threshold, userId)

    override fun deleteThreshold(
        id: Long,
        userId: Long,
    ) = thresholdRepository.deleteByIdAndUserId(id, userId)

    override fun getThresholdStatus(query: GetThresholdStatusQuery): ThresholdStatusResponse {
        val thresholds = thresholdRepository.findAllByUserId(query.userId)
        if (thresholds.isEmpty()) return ThresholdStatusResponse(emptyList())

        val transactions =
            transactionRepository.findNegativeByAccountingDateBetween(
                query.from,
                query.to,
                query.userId,
                query.accountIban,
            )

        val spendingMap = mutableMapOf<Pair<String, String?>, BigDecimal>()
        for (tx in transactions) {
            val cat = tx.category ?: continue
            val eff = tx.effectiveAmount().abs()
            spendingMap.merge(cat to null, eff, BigDecimal::add)
            val sub = tx.subcategory
            if (sub != null) spendingMap.merge(cat to sub, eff, BigDecimal::add)
        }

        val items =
            thresholds
                .groupBy { it.category to it.subcategory }
                .mapNotNull { (key, keyThresholds) ->
                    val best = pickBest(keyThresholds, query.from, query.to) ?: return@mapNotNull null
                    val spending = spendingMap[key] ?: BigDecimal.ZERO
                    buildStatusItem(best, spending, query.from, query.to)
                }

        return ThresholdStatusResponse(items)
    }

    private fun pickBest(
        thresholds: List<Threshold>,
        from: LocalDate,
        to: LocalDate,
    ): Threshold? {
        if (thresholds.isEmpty()) return null
        if (thresholds.size == 1) return thresholds.first()
        val days =
            java.time.temporal.ChronoUnit.DAYS
                .between(from, to) + 1L
        val idealDays =
            mapOf(
                ThresholdPeriod.WEEKLY to 7L,
                ThresholdPeriod.MONTHLY to 30L,
                ThresholdPeriod.QUARTERLY to 91L,
                ThresholdPeriod.YEARLY to 365L,
            )
        return thresholds.minByOrNull { t ->
            val ideal = idealDays[t.period] ?: 30L
            abs(ln(days.toDouble() / ideal.toDouble()))
        }
    }

    private fun periodsInRange(
        from: LocalDate,
        to: LocalDate,
        period: ThresholdPeriod,
    ): BigDecimal {
        val days =
            java.time.temporal.ChronoUnit.DAYS
                .between(from, to) + 1L
        val months = (to.year - from.year) * 12L + (to.monthValue - from.monthValue) + 1L
        return when (period) {
            ThresholdPeriod.WEEKLY -> BigDecimal(days).divide(BigDecimal(7), 6, RoundingMode.HALF_UP)
            ThresholdPeriod.MONTHLY -> BigDecimal(months)
            ThresholdPeriod.QUARTERLY -> BigDecimal(months).divide(BigDecimal(3), 6, RoundingMode.HALF_UP)
            ThresholdPeriod.YEARLY -> BigDecimal(months).divide(BigDecimal(12), 6, RoundingMode.HALF_UP)
        }
    }

    private fun buildStatusItem(
        threshold: Threshold,
        spending: BigDecimal,
        from: LocalDate,
        to: LocalDate,
    ): ThresholdStatusItem? {
        val p = periodsInRange(from, to, threshold.period)
        val sN = threshold.notice?.multiply(p)
        val sW = threshold.warning?.multiply(p)
        val sC = threshold.critical?.multiply(p)
        val max = sC ?: sW ?: sN ?: return null
        if (max <= BigDecimal.ZERO) return null

        val pct = spending.divide(max, 6, RoundingMode.HALF_UP)
        val status =
            when {
                sC != null && spending >= sC -> ThresholdStatus.CRITICAL
                sW != null && spending >= sW -> ThresholdStatus.WARNING
                sN != null && spending >= sN -> ThresholdStatus.NOTICE
                else -> ThresholdStatus.OK
            }
        val tickNotice = sN?.divide(max, 6, RoundingMode.HALF_UP)?.let { it.min(BigDecimal.ONE) }
        val tickWarning = sW?.divide(max, 6, RoundingMode.HALF_UP)?.let { it.min(BigDecimal.ONE) }

        return ThresholdStatusItem(
            thresholdId = threshold.id,
            category = threshold.category,
            subcategory = threshold.subcategory,
            categoryGroup = threshold.categoryGroup,
            period = threshold.period,
            notice = threshold.notice,
            warning = threshold.warning,
            critical = threshold.critical,
            spending = spending,
            periodsInRange = p,
            pct = pct,
            tickNotice = tickNotice,
            tickWarning = tickWarning,
            status = status,
        )
    }
}
