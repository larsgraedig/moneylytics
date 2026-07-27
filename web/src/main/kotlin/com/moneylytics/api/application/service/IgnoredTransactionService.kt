package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.FindIgnoredFingerprintsUseCase
import com.moneylytics.api.application.port.input.UpdateIgnoredTransactionsUseCase
import com.moneylytics.api.application.port.output.IgnoredTransactionRepository
import org.springframework.stereotype.Service

@Service
class IgnoredTransactionService(
    private val repository: IgnoredTransactionRepository,
) : FindIgnoredFingerprintsUseCase,
    UpdateIgnoredTransactionsUseCase {
    override fun findIgnoredFingerprints(
        fingerprints: Collection<String>,
        organizationId: Long,
    ): Set<String> = if (fingerprints.isEmpty()) emptySet() else repository.findExistingFingerprints(fingerprints, organizationId)

    override fun update(
        toIgnore: Collection<String>,
        toUnignore: Collection<String>,
        organizationId: Long,
    ) {
        if (toIgnore.isNotEmpty()) repository.saveAll(toIgnore, organizationId)
        if (toUnignore.isNotEmpty()) repository.deleteAll(toUnignore, organizationId)
    }
}
