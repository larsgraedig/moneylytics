package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.TransactionImportRepository
import com.moneylytics.api.domain.ImportFileType
import com.moneylytics.api.domain.ImportStatus
import com.moneylytics.api.domain.TransactionImport
import com.moneylytics.api.domain.TransactionImportFile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionImportPersistenceAdapter(
    private val jpaRepository: TransactionImportJpaRepository,
    private val importFileJpaRepository: TransactionImportFileJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
) : TransactionImportRepository {
    @Transactional
    override fun save(import: TransactionImport): TransactionImport {
        val entity =
            TransactionImportEntity(
                organization = organizationJpaRepository.getReferenceById(import.organizationId),
                importedAt = import.importedAt,
                status = import.status.name,
            )
        return jpaRepository.save(entity).toDomain()
    }

    @Transactional(readOnly = true)
    override fun findAllByOrganizationId(organizationId: Long): List<TransactionImport> {
        val imports = jpaRepository.findByOrganizationIdOrderByImportedAtDesc(organizationId)
        val importIds = imports.mapNotNull { it.id }
        val filesByImportId =
            if (importIds.isEmpty()) {
                emptyMap()
            } else {
                importFileJpaRepository.findByImportIdIn(importIds).groupBy { it.import.id!! }
            }
        return imports.map { it.toDomain(filesByImportId[it.id] ?: emptyList()) }
    }

    @Transactional(readOnly = true)
    override fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): TransactionImport? {
        val entity = jpaRepository.findByIdAndOrganizationId(id, organizationId) ?: return null
        val files = importFileJpaRepository.findByImportId(id)
        return entity.toDomain(files)
    }

    @Transactional
    override fun updateStatus(
        id: Long,
        status: ImportStatus,
    ) {
        val entity = jpaRepository.findById(id).orElse(null) ?: return
        entity.status = status.name
        jpaRepository.save(entity)
    }

    private fun TransactionImportEntity.toDomain(files: List<TransactionImportFileEntity> = emptyList()) =
        TransactionImport(
            id = id,
            organizationId = organization.id!!,
            importedAt = importedAt,
            status = ImportStatus.valueOf(status),
            files =
                files.map { f ->
                    TransactionImportFile(
                        id = f.id,
                        importId = id!!,
                        filename = f.filename,
                        checksum = f.checksum,
                        fileType = ImportFileType.valueOf(f.fileType),
                        transactionCount = f.transactionCount,
                        status = ImportStatus.valueOf(f.status),
                    )
                },
        )
}
