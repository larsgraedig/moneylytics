package com.moneylytics.api.application.service

import com.moneylytics.api.adapter.output.persistence.BudgetTransactionJpaRepository
import com.moneylytics.api.adapter.output.persistence.CollectionTransactionJpaRepository
import com.moneylytics.api.adapter.output.persistence.TransactionJpaRepository
import com.moneylytics.api.adapter.output.persistence.TransactionOffsetJpaRepository
import com.moneylytics.api.application.port.input.BlockedTransaction
import com.moneylytics.api.application.port.input.GetImportsUseCase
import com.moneylytics.api.application.port.input.RejectImportResult
import com.moneylytics.api.application.port.input.RejectImportUseCase
import com.moneylytics.api.application.port.output.TransactionImportRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.ImportStatus
import com.moneylytics.api.domain.TransactionImport
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ImportManagementService(
    private val transactionImportRepository: TransactionImportRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionJpaRepository: TransactionJpaRepository,
    private val collectionTransactionJpaRepository: CollectionTransactionJpaRepository,
    private val budgetTransactionJpaRepository: BudgetTransactionJpaRepository,
    private val offsetJpaRepository: TransactionOffsetJpaRepository,
) : GetImportsUseCase,
    RejectImportUseCase {
    override fun getImports(organizationId: Long): List<TransactionImport> =
        transactionImportRepository.findAllByOrganizationId(organizationId)

    @Transactional
    override fun rejectImport(
        importId: Long,
        organizationId: Long,
        force: Boolean,
    ): RejectImportResult {
        val import =
            transactionImportRepository.findByIdAndOrganizationId(importId, organizationId)
                ?: return RejectImportResult.Failure(emptyList())

        if (import.status == ImportStatus.REJECTED) {
            return RejectImportResult.Failure(emptyList())
        }

        val txIds = transactionRepository.findIdsByImportId(importId)

        val blocked =
            txIds.mapNotNull { txId ->
                val reasons =
                    buildList {
                        val entity = transactionJpaRepository.findById(txId).orElse(null)
                        if (entity?.parentId != null) add("HAS_PARENT")
                        if (transactionJpaRepository.existsByParentIdAndExcludedFalse(txId)) add("IS_PARENT")
                        if (collectionTransactionJpaRepository.existsByTransactionId(txId)) add("IN_COLLECTION")
                        if (budgetTransactionJpaRepository.existsByTransactionId(txId)) add("IN_BUDGET")
                        if (offsetJpaRepository.existsByTransactionId(txId)) add("HAS_OFFSET")
                    }
                if (reasons.isNotEmpty()) BlockedTransaction(txId, reasons) else null
            }

        if (blocked.isNotEmpty() && !force) return RejectImportResult.Failure(blocked)

        val blockedIds = blocked.map { it.transactionId }.toSet()
        val toReject = txIds.filter { it !in blockedIds }
        transactionRepository.excludeByIds(toReject, organizationId)
        transactionImportRepository.updateStatus(importId, ImportStatus.REJECTED)
        return RejectImportResult.Success(toReject.size)
    }
}
