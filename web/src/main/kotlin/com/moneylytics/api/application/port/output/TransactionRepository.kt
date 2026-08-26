package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Transaction
import java.time.LocalDate

data class CategoryUpdateEntry(
    val id: Long,
    val categoryId: Long?,
)

interface TransactionRepository {
    fun saveAll(
        transactions: List<Transaction>,
        organizationId: Long,
    ): Pair<Int, List<Long>>

    fun linkToImport(
        importId: Long,
        transactionIds: List<Long>,
    )

    fun excludeByImportId(
        importId: Long,
        organizationId: Long,
    )

    fun excludeByIds(
        ids: List<Long>,
        organizationId: Long,
    )

    fun findIdsByFingerprints(
        fingerprints: List<String>,
        organizationId: Long,
    ): List<Long>

    fun linkToImportFile(
        importFileId: Long,
        transactionIds: List<Long>,
    )

    fun findIdsByImportId(importId: Long): List<Long>

    fun findByImportId(
        importId: Long,
        organizationId: Long,
    ): List<Transaction>

    fun findByImportFileId(
        fileId: Long,
        organizationId: Long,
    ): List<Transaction>

    fun findByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        organizationId: Long,
        accountId: Long? = null,
    ): List<Transaction>

    fun findNegativeByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        organizationId: Long,
        accountId: Long? = null,
    ): List<Transaction>

    fun updateAccountingDate(
        id: Long,
        organizationId: Long,
        accountingDate: LocalDate,
    ): Transaction?

    fun findExistingFingerprints(
        fingerprints: Collection<String>,
        organizationId: Long,
    ): Set<String>

    fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): Transaction?

    fun updateCategory(
        id: Long,
        organizationId: Long,
        categoryId: Long?,
    ): Transaction?

    fun updateComment(
        id: Long,
        organizationId: Long,
        comment: String?,
    ): Transaction?

    fun findByIdsAndOrganizationId(
        ids: Set<Long>,
        organizationId: Long,
    ): List<Transaction>

    fun enrichByFingerprint(
        fingerprint: String,
        organizationId: Long,
        purpose: String?,
        counterpartyName: String?,
        counterpartyIban: String?,
    )

    fun latestTransactionDatesByOrganizationId(organizationId: Long): Map<String, LocalDate>

    fun findAssignedTransactionIdsByCollectionId(collectionId: Long): Set<Long>

    fun bulkUpdateCategory(
        updates: List<CategoryUpdateEntry>,
        organizationId: Long,
    ): List<Transaction>

    fun countByCategoryGrouped(
        organizationId: Long,
        accountId: Long? = null,
    ): Map<Long, Long>

    fun countByCategoryGroupedInPeriod(
        organizationId: Long,
        from: LocalDate,
        to: LocalDate,
        accountId: Long? = null,
    ): Map<Long, Long>

    fun findIdsByCategoryId(
        categoryId: Long,
        organizationId: Long,
    ): List<Long>

    fun moveToCategoryBySource(
        sourceCategoryId: Long,
        targetCategoryId: Long,
        organizationId: Long,
    )

    fun moveToCategoryBulk(
        transactionIds: List<Long>,
        targetCategoryId: Long,
        organizationId: Long,
    )
}
