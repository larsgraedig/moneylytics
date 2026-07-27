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
    companion object {
        private const val DAYS_PER_WEEK = 7L
        private const val IDEAL_DAYS_MONTHLY = 30L
        private const val IDEAL_DAYS_QUARTERLY = 91L
        private const val IDEAL_DAYS_YEARLY = 365L
        private const val MONTHS_PER_YEAR = 12L
        private const val MONTHS_PER_QUARTER = 3
        private const val DIVISION_SCALE = 6
    }

    override fun getThresholds(organizationId: Long): List<Threshold> = thresholdRepository.findAllByOrganizationId(organizationId)

    override fun saveThreshold(
        threshold: Threshold,
        organizationId: Long,
    ): Threshold = thresholdRepository.upsert(threshold, organizationId)

    override fun deleteThreshold(
        id: Long,
        organizationId: Long,
    ) = thresholdRepository.deleteByIdAndOrganizationId(id, organizationId)

    override fun getThresholdStatus(query: GetThresholdStatusQuery): ThresholdStatusResponse {
        val thresholds = thresholdRepository.findAllByOrganizationId(query.organizationId)
        if (thresholds.isEmpty()) return ThresholdStatusResponse(emptyList())

        val transactions =
            transactionRepository.findNegativeByAccountingDateBetween(
                query.from,
                query.to,
                query.organizationId,
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
                ThresholdPeriod.WEEKLY to DAYS_PER_WEEK,
                ThresholdPeriod.MONTHLY to IDEAL_DAYS_MONTHLY,
                ThresholdPeriod.QUARTERLY to IDEAL_DAYS_QUARTERLY,
                ThresholdPeriod.YEARLY to IDEAL_DAYS_YEARLY,
            )
        return thresholds.minByOrNull { t ->
            val ideal = idealDays[t.period] ?: IDEAL_DAYS_MONTHLY
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
        val months = (to.year - from.year) * MONTHS_PER_YEAR + (to.monthValue - from.monthValue) + 1L
        return when (period) {
            ThresholdPeriod.WEEKLY -> BigDecimal(days).divide(BigDecimal(DAYS_PER_WEEK), DIVISION_SCALE, RoundingMode.HALF_UP)
            ThresholdPeriod.MONTHLY -> BigDecimal(months)
            ThresholdPeriod.QUARTERLY -> BigDecimal(months).divide(BigDecimal(MONTHS_PER_QUARTER), DIVISION_SCALE, RoundingMode.HALF_UP)
            ThresholdPeriod.YEARLY -> BigDecimal(months).divide(BigDecimal(MONTHS_PER_YEAR), DIVISION_SCALE, RoundingMode.HALF_UP)
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

        val pct = spending.divide(max, DIVISION_SCALE, RoundingMode.HALF_UP)
        val status =
            when {
                sC != null && spending >= sC -> ThresholdStatus.CRITICAL
                sW != null && spending >= sW -> ThresholdStatus.WARNING
                sN != null && spending >= sN -> ThresholdStatus.NOTICE
                else -> ThresholdStatus.OK
            }
        val tickNotice = sN?.divide(max, DIVISION_SCALE, RoundingMode.HALF_UP)?.let { it.min(BigDecimal.ONE) }
        val tickWarning = sW?.divide(max, DIVISION_SCALE, RoundingMode.HALF_UP)?.let { it.min(BigDecimal.ONE) }

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
