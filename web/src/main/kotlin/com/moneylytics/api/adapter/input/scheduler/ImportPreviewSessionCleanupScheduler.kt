package com.moneylytics.api.adapter.input.scheduler

import com.moneylytics.api.adapter.output.persistence.ImportPreviewSessionPersistenceAdapter
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ImportPreviewSessionCleanupScheduler(
    private val importPreviewSessionPersistenceAdapter: ImportPreviewSessionPersistenceAdapter,
) {
    @Scheduled(cron = "0 0 3 * * *")
    fun cleanup() = importPreviewSessionPersistenceAdapter.deleteExpired()
}
