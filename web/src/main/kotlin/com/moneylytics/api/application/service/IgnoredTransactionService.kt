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
    override fun findIgnoredFingerprints(fingerprints: Collection<String>): Set<String> =
        if (fingerprints.isEmpty()) emptySet() else repository.findExistingFingerprints(fingerprints)

    override fun update(
        toIgnore: Collection<String>,
        toUnignore: Collection<String>,
    ) {
        if (toIgnore.isNotEmpty()) repository.saveAll(toIgnore)
        if (toUnignore.isNotEmpty()) repository.deleteAll(toUnignore)
    }
}
