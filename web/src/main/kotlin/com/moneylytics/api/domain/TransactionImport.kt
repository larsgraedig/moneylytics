package com.moneylytics.api.domain

import java.time.Instant

enum class ImportStatus { ACTIVE, REJECTED }

enum class ImportFileType { CSV, CAMT, GENERIC }

data class TransactionImport(
    val id: Long? = null,
    val organizationId: Long,
    val importedAt: Instant = Instant.now(),
    val filename: String,
    val checksum: String,
    val fileType: ImportFileType,
    val transactionCount: Int,
    val status: ImportStatus = ImportStatus.ACTIVE,
)
