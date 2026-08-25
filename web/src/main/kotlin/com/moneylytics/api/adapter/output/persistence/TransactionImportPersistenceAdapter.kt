package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.TransactionImportRepository
import com.moneylytics.api.domain.ImportFileType
import com.moneylytics.api.domain.ImportStatus
import com.moneylytics.api.domain.TransactionImport
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionImportPersistenceAdapter(
    private val jpaRepository: TransactionImportJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
) : TransactionImportRepository {
    @Transactional
    override fun save(import: TransactionImport): TransactionImport {
        val entity =
            TransactionImportEntity(
                organization = organizationJpaRepository.getReferenceById(import.organizationId),
                importedAt = import.importedAt,
                filename = import.filename,
                checksum = import.checksum,
                fileType = import.fileType.name,
                transactionCount = import.transactionCount,
                status = import.status.name,
            )
        return jpaRepository.save(entity).toDomain()
    }

    @Transactional(readOnly = true)
    override fun findAllByOrganizationId(organizationId: Long): List<TransactionImport> =
        jpaRepository.findByOrganizationIdOrderByImportedAtDesc(organizationId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): TransactionImport? = jpaRepository.findByIdAndOrganizationId(id, organizationId)?.toDomain()

    @Transactional
    override fun updateStatus(
        id: Long,
        status: ImportStatus,
    ) {
        val entity = jpaRepository.findById(id).orElse(null) ?: return
        entity.status = status.name
        jpaRepository.save(entity)
    }

    private fun TransactionImportEntity.toDomain() =
        TransactionImport(
            id = id,
            organizationId = organization.id!!,
            importedAt = importedAt,
            filename = filename,
            checksum = checksum,
            fileType = ImportFileType.valueOf(fileType),
            transactionCount = transactionCount,
            status = ImportStatus.valueOf(status),
        )
}
