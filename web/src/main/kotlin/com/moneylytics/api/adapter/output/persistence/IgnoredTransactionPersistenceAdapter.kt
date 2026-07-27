package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.IgnoredTransactionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IgnoredTransactionPersistenceAdapter(
    private val jpaRepository: IgnoredTransactionJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
) : IgnoredTransactionRepository {
    @Transactional(readOnly = true)
    override fun findExistingFingerprints(
        fingerprints: Collection<String>,
        organizationId: Long,
    ): Set<String> = jpaRepository.findExistingFingerprints(fingerprints, organizationId).toHashSet()

    @Transactional
    override fun saveAll(
        fingerprints: Collection<String>,
        organizationId: Long,
    ) {
        val existing = jpaRepository.findExistingFingerprints(fingerprints, organizationId).toHashSet()
        val organization = organizationJpaRepository.getReferenceById(organizationId)
        val toSave =
            fingerprints
                .filter { it !in existing }
                .map { IgnoredTransactionEntity(fingerprint = it, organization = organization) }
        if (toSave.isNotEmpty()) jpaRepository.saveAll(toSave)
    }

    @Transactional
    override fun deleteAll(
        fingerprints: Collection<String>,
        organizationId: Long,
    ) {
        jpaRepository.deleteByFingerprintInAndOrganizationId(fingerprints, organizationId)
    }
}
