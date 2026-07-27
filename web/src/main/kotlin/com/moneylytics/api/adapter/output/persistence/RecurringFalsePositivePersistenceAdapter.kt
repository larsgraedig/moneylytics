package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.RecurringFalsePositiveRepository
import com.moneylytics.api.domain.RecurringFalsePositive
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RecurringFalsePositivePersistenceAdapter(
    private val jpaRepository: RecurringFalsePositiveJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
) : RecurringFalsePositiveRepository {
    override fun findFingerprintsByOrganizationId(organizationId: Long): Set<String> =
        jpaRepository.findByOrganizationId(organizationId).map { it.fingerprint }.toSet()

    @Transactional
    override fun saveAll(entries: List<RecurringFalsePositive>) {
        val organization = organizationJpaRepository.getReferenceById(entries.first().organizationId)
        jpaRepository.saveAll(
            entries.map { e ->
                RecurringFalsePositiveEntity(organization = organization, fingerprint = e.fingerprint, createdAt = e.createdAt)
            },
        )
    }

    @Transactional
    override fun deleteByOrganizationIdAndFingerprints(
        organizationId: Long,
        fingerprints: List<String>,
    ) {
        jpaRepository.deleteByOrganizationIdAndFingerprintIn(organizationId, fingerprints)
    }
}
