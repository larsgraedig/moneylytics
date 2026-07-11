package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Transaction
import java.time.LocalDate

interface TransactionRepository {
    fun saveAll(
        transactions: List<Transaction>,
        userId: Long,
    ): Int

    fun findByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        userId: Long,
        accountIban: String? = null,
    ): List<Transaction>

    fun findNegativeByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        userId: Long,
        accountIban: String? = null,
    ): List<Transaction>

    fun updateAccountingDate(
        id: Long,
        userId: Long,
        accountingDate: LocalDate,
    ): Transaction?

    fun findExistingFingerprints(
        fingerprints: Collection<String>,
        userId: Long,
    ): Set<String>

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Transaction?

    fun updateCategory(
        id: Long,
        userId: Long,
        category: String,
        subcategory: String,
        categoryGroup: String? = null,
    ): Transaction?

    fun updateComment(
        id: Long,
        userId: Long,
        comment: String?,
    ): Transaction?

    fun findByIdsAndUserId(
        ids: Set<Long>,
        userId: Long,
    ): List<Transaction>

    fun enrichByFingerprint(
        fingerprint: String,
        userId: Long,
        purpose: String?,
        counterpartyName: String?,
        counterpartyIban: String?,
        categoryGroup: String? = null,
    )

    fun latestTransactionDatesByUserId(userId: Long): Map<String, LocalDate>

    fun findAssignedTransactionIdsByCollectionId(collectionId: Long): Set<Long>
}
