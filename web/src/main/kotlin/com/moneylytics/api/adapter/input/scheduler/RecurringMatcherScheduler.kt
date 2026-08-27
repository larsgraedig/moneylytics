package com.moneylytics.api.adapter.input.scheduler

import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class RecurringMatcherScheduler(
    private val syncRecurringSeriesUseCase: SyncRecurringSeriesUseCase,
) {
    @Scheduled(cron = "0 0 3 * * *")
    @Suppress("TooGenericExceptionCaught")
    fun sync() {
        try {
            syncRecurringSeriesUseCase.syncForAllOrganizations()
        } catch (e: Exception) {
            logger.error(e) { "Recurring series sync failed" }
        }
    }
}
