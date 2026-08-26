package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.ImportStatus
import com.moneylytics.api.domain.TransactionImportFile

interface TransactionImportFileRepository {
    fun save(file: TransactionImportFile): TransactionImportFile

    fun findAllByImportId(importId: Long): List<TransactionImportFile>

    fun findByIdAndImportId(
        fileId: Long,
        importId: Long,
    ): TransactionImportFile?

    fun updateStatus(
        fileId: Long,
        status: ImportStatus,
    )

    fun allFilesFullyRejected(importId: Long): Boolean

    fun anyFileRejectedOrPartial(importId: Long): Boolean

    fun findTransactionIdsByFileId(fileId: Long): List<Long>
}
