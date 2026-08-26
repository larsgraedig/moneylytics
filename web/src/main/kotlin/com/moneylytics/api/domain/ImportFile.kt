package com.moneylytics.api.domain

data class ImportFile(
    val id: Long? = null,
    val importId: Long,
    val filename: String,
    val checksum: String,
    val fileType: ImportFileType,
    val transactionCount: Int,
    val status: ImportStatus = ImportStatus.ACTIVE,
)
