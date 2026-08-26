package com.moneylytics.api.application.port.input

sealed class RejectImportFileResult {
    data class Success(
        val rejectedCount: Int,
    ) : RejectImportFileResult()

    data class Failure(
        val blockedTransactions: List<BlockedTransaction>,
    ) : RejectImportFileResult()
}

interface RejectImportFileUseCase {
    fun rejectImportFile(
        fileId: Long,
        importId: Long,
        organizationId: Long,
        force: Boolean = false,
    ): RejectImportFileResult
}
