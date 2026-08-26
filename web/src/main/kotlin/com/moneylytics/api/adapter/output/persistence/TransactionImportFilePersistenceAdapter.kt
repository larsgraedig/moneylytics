package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.TransactionImportFileRepository
import com.moneylytics.api.domain.ImportFileType
import com.moneylytics.api.domain.ImportStatus
import com.moneylytics.api.domain.TransactionImportFile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionImportFilePersistenceAdapter(
    private val jpaRepository: TransactionImportFileJpaRepository,
    private val transactionImportJpaRepository: TransactionImportJpaRepository,
) : TransactionImportFileRepository {
    @Transactional
    override fun save(file: TransactionImportFile): TransactionImportFile {
        val entity =
            TransactionImportFileEntity(
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
    override fun findAllByImportId(importId: Long): List<TransactionImportFile> =
        jpaRepository.findByImportId(importId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findByIdAndImportId(
        fileId: Long,
        importId: Long,
    ): TransactionImportFile? = jpaRepository.findByIdAndImportId(fileId, importId)?.toDomain()

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

    private fun TransactionImportFileEntity.toDomain() =
        TransactionImportFile(
            id = id,
            importId = import.id!!,
            filename = filename,
            checksum = checksum,
            fileType = ImportFileType.valueOf(fileType),
            transactionCount = transactionCount,
            status = ImportStatus.valueOf(status),
        )
}
