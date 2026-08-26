package com.moneylytics.api.domain

import java.time.Instant

enum class ImportStatus { ACTIVE, REJECTED, PARTIALLY_REJECTED }

enum class ImportFileType { CSV, CAMT, GENERIC }

data class TransactionImport(
    val id: Long? = null,
    val organizationId: Long,
    val importedAt: Instant = Instant.now(),
    val status: ImportStatus = ImportStatus.ACTIVE,
    val files: List<ImportFile> = emptyList(),
) {
    val transactionCount: Int get() = files.sumOf { it.transactionCount }
}
