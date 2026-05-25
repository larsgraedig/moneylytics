package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.IgnoredTransactionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IgnoredTransactionPersistenceAdapter(
    private val jpaRepository: IgnoredTransactionJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : IgnoredTransactionRepository {
    @Transactional(readOnly = true)
    override fun findExistingFingerprints(
        fingerprints: Collection<String>,
        userId: Long,
    ): Set<String> = jpaRepository.findExistingFingerprints(fingerprints, userId).toHashSet()

    @Transactional
    override fun saveAll(
        fingerprints: Collection<String>,
        userId: Long,
    ) {
        val existing = jpaRepository.findExistingFingerprints(fingerprints, userId).toHashSet()
        val user = userJpaRepository.getReferenceById(userId)
        val toSave =
            fingerprints
                .filter { it !in existing }
                .map { IgnoredTransactionEntity(fingerprint = it, user = user) }
        if (toSave.isNotEmpty()) jpaRepository.saveAll(toSave)
    }

    @Transactional
    override fun deleteAll(
        fingerprints: Collection<String>,
        userId: Long,
    ) {
        jpaRepository.deleteByFingerprintInAndUserId(fingerprints, userId)
    }
}
