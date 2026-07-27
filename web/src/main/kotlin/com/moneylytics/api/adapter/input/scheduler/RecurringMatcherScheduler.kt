package com.moneylytics.api.adapter.input.scheduler

import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RecurringMatcherScheduler(
    private val syncRecurringSeriesUseCase: SyncRecurringSeriesUseCase,
) {
    @Scheduled(cron = "0 0 3 * * *")
    fun sync() = syncRecurringSeriesUseCase.syncForAllOrganizations()
}
