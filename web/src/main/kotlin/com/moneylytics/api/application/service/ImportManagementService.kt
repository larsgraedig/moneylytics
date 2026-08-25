package com.moneylytics.api.application.service

import com.moneylytics.api.adapter.output.persistence.BudgetTransactionJpaRepository
import com.moneylytics.api.adapter.output.persistence.CollectionTransactionJpaRepository
import com.moneylytics.api.adapter.output.persistence.TransactionJpaRepository
import com.moneylytics.api.adapter.output.persistence.TransactionOffsetJpaRepository
import com.moneylytics.api.application.port.input.BlockedTransaction
import com.moneylytics.api.application.port.input.GetImportTransactionsQuery
import com.moneylytics.api.application.port.input.GetImportTransactionsUseCase
import com.moneylytics.api.application.port.input.GetImportsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionItem
import com.moneylytics.api.application.port.input.RejectImportFileResult
import com.moneylytics.api.application.port.input.RejectImportFileUseCase
import com.moneylytics.api.application.port.input.RejectImportResult
import com.moneylytics.api.application.port.input.RejectImportUseCase
import com.moneylytics.api.application.port.output.ImportFileRepository
import com.moneylytics.api.application.port.output.TransactionImportRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.ImportStatus
import com.moneylytics.api.domain.Transaction
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
    private val importFileRepository: ImportFileRepository,
) : GetImportsUseCase,
    RejectImportUseCase,
    RejectImportFileUseCase,
    GetImportTransactionsUseCase {
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

        val blocked = collectBlocked(txIds)

        if (blocked.isNotEmpty() && !force) return RejectImportResult.Failure(blocked)

        val blockedIds = blocked.map { it.transactionId }.toSet()
        val toReject = txIds.filter { it !in blockedIds }
        transactionRepository.excludeByIds(toReject, organizationId)

        val fileStatus = if (blocked.isNotEmpty()) ImportStatus.PARTIALLY_REJECTED else ImportStatus.REJECTED
        import.files.filter { it.status == ImportStatus.ACTIVE }.forEach { file ->
            importFileRepository.updateStatus(requireNotNull(file.id), fileStatus)
        }
        val importStatus = if (blocked.isNotEmpty()) ImportStatus.PARTIALLY_REJECTED else ImportStatus.REJECTED
        transactionImportRepository.updateStatus(importId, importStatus)
        return RejectImportResult.Success(toReject.size)
    }

    @Transactional
    override fun rejectImportFile(
        fileId: Long,
        importId: Long,
        organizationId: Long,
        force: Boolean,
    ): RejectImportFileResult {
        val import =
            transactionImportRepository.findByIdAndOrganizationId(importId, organizationId)
                ?: return RejectImportFileResult.Failure(emptyList())

        val file =
            importFileRepository.findByIdAndImportId(fileId, importId)
                ?: return RejectImportFileResult.Failure(emptyList())

        if (file.status == ImportStatus.REJECTED) {
            return RejectImportFileResult.Failure(emptyList())
        }

        val txIds = importFileRepository.findTransactionIdsByFileId(fileId)

        val blocked = collectBlocked(txIds)

        if (blocked.isNotEmpty() && !force) return RejectImportFileResult.Failure(blocked)

        val blockedIds = blocked.map { it.transactionId }.toSet()
        val toReject = txIds.filter { it !in blockedIds }
        transactionRepository.excludeByIds(toReject, organizationId)
        val fileStatus = if (blocked.isNotEmpty()) ImportStatus.PARTIALLY_REJECTED else ImportStatus.REJECTED
        importFileRepository.updateStatus(fileId, fileStatus)

        when {
            importFileRepository.allFilesFullyRejected(importId) ->
                transactionImportRepository.updateStatus(importId, ImportStatus.REJECTED)
            importFileRepository.anyFileRejectedOrPartial(importId) ->
                transactionImportRepository.updateStatus(importId, ImportStatus.PARTIALLY_REJECTED)
        }

        return RejectImportFileResult.Success(toReject.size)
    }

    override fun getImportTransactions(query: GetImportTransactionsQuery): List<ImportTransactionItem> =
        transactionImportRepository
            .findByIdAndOrganizationId(query.importId, query.organizationId)
            ?.let {
                transactionRepository
                    .findByImportId(query.importId, query.organizationId)
                    .map { it.toImportTransactionItem() }
            }
            ?: emptyList()

    private fun Transaction.toImportTransactionItem() =
        ImportTransactionItem(
            id = requireNotNull(id),
            bookingDate = bookingDate,
            counterpartyName = counterpartyName,
            purpose = purpose,
            amount = amount,
            currency = currency,
            excluded = excluded,
            collections = collections,
            budgetLinks = budgetLinks,
            groups = groups,
            parentId = parentId,
            isVirtual = isVirtual,
        )

    private fun collectBlocked(txIds: List<Long>): List<BlockedTransaction> =
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
}
