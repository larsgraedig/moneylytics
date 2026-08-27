package com.moneylytics.api.adapter.input.scheduler

import com.moneylytics.api.adapter.output.persistence.TransactionImportPreviewSessionPersistenceAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class TransactionImportPreviewSessionCleanupScheduler(
    private val transactionImportPreviewSessionPersistenceAdapter: TransactionImportPreviewSessionPersistenceAdapter,
) {
    @Scheduled(cron = "0 0 3 * * *")
    @Suppress("TooGenericExceptionCaught")
    fun cleanup() {
        try {
            transactionImportPreviewSessionPersistenceAdapter.deleteExpired()
        } catch (e: Exception) {
            logger.error(e) { "Import preview session cleanup failed" }
        }
    }
}
