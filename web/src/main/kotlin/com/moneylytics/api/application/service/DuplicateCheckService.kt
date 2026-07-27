package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.output.TransactionRepository
import org.springframework.stereotype.Service

@Service
class DuplicateCheckService(
    private val transactionRepository: TransactionRepository,
) : CheckDuplicatesUseCase {
    override fun findExistingFingerprints(
        fingerprints: Collection<String>,
        organizationId: Long,
    ): Set<String> = transactionRepository.findExistingFingerprints(fingerprints, organizationId)
}
