package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.RecurringFalsePositiveRepository
import com.moneylytics.api.domain.RecurringFalsePositive
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RecurringFalsePositivePersistenceAdapter(
    private val jpaRepository: RecurringFalsePositiveJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : RecurringFalsePositiveRepository {
    override fun findFingerprintsByUserId(userId: Long): Set<String> = jpaRepository.findByUserId(userId).map { it.fingerprint }.toSet()

    @Transactional
    override fun saveAll(entries: List<RecurringFalsePositive>) {
        val user = userJpaRepository.getReferenceById(entries.first().userId)
        jpaRepository.saveAll(
            entries.map { e ->
                RecurringFalsePositiveEntity(user = user, fingerprint = e.fingerprint, createdAt = e.createdAt)
            },
        )
    }

    @Transactional
    override fun deleteByUserIdAndFingerprints(
        userId: Long,
        fingerprints: List<String>,
    ) {
        jpaRepository.deleteByUserIdAndFingerprintIn(userId, fingerprints)
    }
}
