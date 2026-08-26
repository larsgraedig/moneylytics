package com.moneylytics.api.adapter.input.scheduler

import com.moneylytics.api.adapter.output.persistence.TransactionImportPreviewSessionPersistenceAdapter
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TransactionImportPreviewSessionCleanupScheduler(
    private val transactionImportPreviewSessionPersistenceAdapter: TransactionImportPreviewSessionPersistenceAdapter,
) {
    @Scheduled(cron = "0 0 3 * * *")
    fun cleanup() = transactionImportPreviewSessionPersistenceAdapter.deleteExpired()
}
