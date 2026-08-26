package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.ImportFileRepository
import com.moneylytics.api.domain.ImportFile
import com.moneylytics.api.domain.ImportFileType
import com.moneylytics.api.domain.ImportStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ImportFilePersistenceAdapter(
    private val jpaRepository: ImportFileJpaRepository,
    private val transactionImportJpaRepository: TransactionImportJpaRepository,
) : ImportFileRepository {
    @Transactional
    override fun save(file: ImportFile): ImportFile {
        val entity =
            ImportFileEntity(
                import = transactionImportJpaRepository.getReferenceById(file.importId),
                filename = file.filename,
                checksum = file.checksum,
                fileType = file.fileType.name,
                transactionCount = file.transactionCount,
                status = file.status.name,
            )
        return jpaRepository.save(entity).toDomain()
    }

    @Transactional(readOnly = true)
    override fun findAllByImportId(importId: Long): List<ImportFile> = jpaRepository.findByImportId(importId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findByIdAndImportId(
        fileId: Long,
        importId: Long,
    ): ImportFile? = jpaRepository.findByIdAndImportId(fileId, importId)?.toDomain()

    @Transactional
    override fun updateStatus(
        fileId: Long,
        status: ImportStatus,
    ) {
        jpaRepository.updateStatus(fileId, status.name)
    }

    @Transactional(readOnly = true)
    override fun allFilesFullyRejected(importId: Long): Boolean = jpaRepository.allFilesFullyRejected(importId)

    @Transactional(readOnly = true)
    override fun anyFileRejectedOrPartial(importId: Long): Boolean = jpaRepository.anyFileRejectedOrPartial(importId)

    @Transactional(readOnly = true)
    override fun findTransactionIdsByFileId(fileId: Long): List<Long> = jpaRepository.findTransactionIdsByImportFileId(fileId)

    private fun ImportFileEntity.toDomain() =
        ImportFile(
            id = id,
            importId = import.id!!,
            filename = filename,
            checksum = checksum,
            fileType = ImportFileType.valueOf(fileType),
            transactionCount = transactionCount,
            status = ImportStatus.valueOf(status),
        )
}
