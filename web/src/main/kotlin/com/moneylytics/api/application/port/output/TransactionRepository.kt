package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Transaction
import java.time.LocalDate

data class CategoryUpdateEntry(
    val id: Long,
    val category: String,
    val subcategory: String,
    val categoryGroup: String?,
)

interface TransactionRepository {
    fun saveAll(
        transactions: List<Transaction>,
        organizationId: Long,
    ): Int

    fun findByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        organizationId: Long,
        accountIban: String? = null,
    ): List<Transaction>

    fun findNegativeByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        organizationId: Long,
        accountIban: String? = null,
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
        category: String,
        subcategory: String,
        categoryGroup: String? = null,
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
        categoryGroup: String? = null,
    )

    fun latestTransactionDatesByOrganizationId(organizationId: Long): Map<String, LocalDate>

    fun findAssignedTransactionIdsByCollectionId(collectionId: Long): Set<Long>

    fun bulkUpdateCategory(
        updates: List<CategoryUpdateEntry>,
        organizationId: Long,
    ): List<Transaction>
}
