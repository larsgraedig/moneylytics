package com.moneylytics.api.application.service

import com.moneylytics.api.adapter.output.persistence.BudgetTransactionJpaRepository
import com.moneylytics.api.adapter.output.persistence.CollectionTransactionJpaRepository
import com.moneylytics.api.adapter.output.persistence.TransactionJpaRepository
import com.moneylytics.api.adapter.output.persistence.TransactionOffsetJpaRepository
import com.moneylytics.api.application.port.input.RejectImportResult
import com.moneylytics.api.application.port.output.TransactionImportRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.ImportFileType
import com.moneylytics.api.domain.ImportStatus
import com.moneylytics.api.domain.TransactionImport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional

class ImportManagementServiceTest {
    private val transactionImportRepository: TransactionImportRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val transactionJpaRepository: TransactionJpaRepository = mock()
    private val collectionTransactionJpaRepository: CollectionTransactionJpaRepository = mock()
    private val budgetTransactionJpaRepository: BudgetTransactionJpaRepository = mock()
    private val offsetJpaRepository: TransactionOffsetJpaRepository = mock()

    private val service =
        ImportManagementService(
            transactionImportRepository,
            transactionRepository,
            transactionJpaRepository,
            collectionTransactionJpaRepository,
            budgetTransactionJpaRepository,
            offsetJpaRepository,
        )

    private val organizationId = 1L
    private val importId = 10L
    private val activeImport =
        TransactionImport(
            id = importId,
            organizationId = organizationId,
            filename = "test.csv",
            checksum = "abc",
            fileType = ImportFileType.CSV,
            transactionCount = 3,
            status = ImportStatus.ACTIVE,
            importedAt = Instant.now(),
        )

    @Test
    fun `should return failure when force=false and blocked transactions exist`() {
        whenever(transactionImportRepository.findByIdAndOrganizationId(importId, organizationId))
            .thenReturn(activeImport)
        whenever(transactionRepository.findIdsByImportId(importId)).thenReturn(listOf(1L, 2L, 3L))
        whenever(transactionJpaRepository.findById(1L)).thenReturn(Optional.empty())
        whenever(transactionJpaRepository.findById(2L)).thenReturn(Optional.empty())
        whenever(transactionJpaRepository.findById(3L)).thenReturn(Optional.empty())
        whenever(transactionJpaRepository.existsByParentIdAndExcludedFalse(any())).thenReturn(false)
        whenever(collectionTransactionJpaRepository.existsByTransactionId(2L)).thenReturn(true)
        whenever(budgetTransactionJpaRepository.existsByTransactionId(any())).thenReturn(false)
        whenever(offsetJpaRepository.existsByTransactionId(any())).thenReturn(false)

        val result = service.rejectImport(importId, organizationId, force = false)

        assertThat(result).isInstanceOf(RejectImportResult.Failure::class.java)
        val failure = result as RejectImportResult.Failure
        assertThat(failure.blockedTransactions).hasSize(1)
        assertThat(failure.blockedTransactions[0].transactionId).isEqualTo(2L)
        verify(transactionRepository, never()).excludeByIds(any(), any())
        verify(transactionRepository, never()).excludeByImportId(any(), any())
    }

    @Test
    fun `should partially reject import when force=true and blocked transactions exist`() {
        whenever(transactionImportRepository.findByIdAndOrganizationId(importId, organizationId))
            .thenReturn(activeImport)
        whenever(transactionRepository.findIdsByImportId(importId)).thenReturn(listOf(1L, 2L, 3L))
        whenever(transactionJpaRepository.findById(1L)).thenReturn(Optional.empty())
        whenever(transactionJpaRepository.findById(2L)).thenReturn(Optional.empty())
        whenever(transactionJpaRepository.findById(3L)).thenReturn(Optional.empty())
        whenever(transactionJpaRepository.existsByParentIdAndExcludedFalse(any())).thenReturn(false)
        whenever(collectionTransactionJpaRepository.existsByTransactionId(2L)).thenReturn(true)
        whenever(budgetTransactionJpaRepository.existsByTransactionId(any())).thenReturn(false)
        whenever(offsetJpaRepository.existsByTransactionId(any())).thenReturn(false)

        val result = service.rejectImport(importId, organizationId, force = true)

        assertThat(result).isInstanceOf(RejectImportResult.Success::class.java)
        val success = result as RejectImportResult.Success
        assertThat(success.rejectedCount).isEqualTo(2)
        verify(transactionRepository).excludeByIds(listOf(1L, 3L), organizationId)
        verify(transactionImportRepository).updateStatus(importId, ImportStatus.REJECTED)
    }

    @Test
    fun `should reject all transactions when none are blocked`() {
        whenever(transactionImportRepository.findByIdAndOrganizationId(importId, organizationId))
            .thenReturn(activeImport)
        whenever(transactionRepository.findIdsByImportId(importId)).thenReturn(listOf(1L, 2L, 3L))
        whenever(transactionJpaRepository.findById(any())).thenReturn(Optional.empty())
        whenever(transactionJpaRepository.existsByParentIdAndExcludedFalse(any())).thenReturn(false)
        whenever(collectionTransactionJpaRepository.existsByTransactionId(any())).thenReturn(false)
        whenever(budgetTransactionJpaRepository.existsByTransactionId(any())).thenReturn(false)
        whenever(offsetJpaRepository.existsByTransactionId(any())).thenReturn(false)

        val result = service.rejectImport(importId, organizationId, force = false)

        assertThat(result).isInstanceOf(RejectImportResult.Success::class.java)
        assertThat((result as RejectImportResult.Success).rejectedCount).isEqualTo(3)
        verify(transactionRepository).excludeByIds(listOf(1L, 2L, 3L), organizationId)
        verify(transactionImportRepository).updateStatus(importId, ImportStatus.REJECTED)
    }
}
