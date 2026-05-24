package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.IgnoredTransactionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IgnoredTransactionPersistenceAdapter(
    private val jpaRepository: IgnoredTransactionJpaRepository,
) : IgnoredTransactionRepository {
    @Transactional(readOnly = true)
    override fun findExistingFingerprints(fingerprints: Collection<String>): Set<String> =
        jpaRepository.findExistingFingerprints(fingerprints).toHashSet()

    @Transactional
    override fun saveAll(fingerprints: Collection<String>) {
        val existing = jpaRepository.findExistingFingerprints(fingerprints).toHashSet()
        val toSave =
            fingerprints
                .filter { it !in existing }
                .map { IgnoredTransactionEntity(fingerprint = it) }
        if (toSave.isNotEmpty()) jpaRepository.saveAll(toSave)
    }

    @Transactional
    override fun deleteAll(fingerprints: Collection<String>) {
        jpaRepository.deleteByFingerprintIn(fingerprints)
    }
}
