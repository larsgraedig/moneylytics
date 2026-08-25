package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.ImportFile
import com.moneylytics.api.domain.ImportStatus

interface ImportFileRepository {
    fun save(file: ImportFile): ImportFile

    fun findAllByImportId(importId: Long): List<ImportFile>

    fun findByIdAndImportId(
        fileId: Long,
        importId: Long,
    ): ImportFile?

    fun updateStatus(
        fileId: Long,
        status: ImportStatus,
    )

    fun allFilesFullyRejected(importId: Long): Boolean

    fun anyFileRejectedOrPartial(importId: Long): Boolean

    fun findTransactionIdsByFileId(fileId: Long): List<Long>
}
