package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.TransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DuplicateCheckServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val service = DuplicateCheckService(transactionRepository)

    private val organizationId = 1L

    @Test
    fun `should return existing fingerprints from repository`() {
        val fingerprints = listOf("fp1", "fp2", "fp3")
        whenever(transactionRepository.findExistingFingerprints(fingerprints, organizationId)).thenReturn(setOf("fp1", "fp3"))

        val result = service.findExistingFingerprints(fingerprints, organizationId)

        assertThat(result).containsExactlyInAnyOrder("fp1", "fp3")
    }
}
