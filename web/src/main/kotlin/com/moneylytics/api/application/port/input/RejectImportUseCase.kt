package com.moneylytics.api.application.port.input

data class BlockedTransaction(
    val transactionId: Long,
    val reasons: List<String>,
)

sealed class RejectImportResult {
    data class Success(
        val rejectedCount: Int,
    ) : RejectImportResult()

    data class Failure(
        val blockedTransactions: List<BlockedTransaction>,
    ) : RejectImportResult()
}

interface RejectImportUseCase {
    fun rejectImport(
        importId: Long,
        organizationId: Long,
        force: Boolean = false,
    ): RejectImportResult
}
