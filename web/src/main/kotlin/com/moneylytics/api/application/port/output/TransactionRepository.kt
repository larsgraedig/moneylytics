package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Transaction
import java.time.LocalDate

interface TransactionRepository {
    fun saveAll(transactions: List<Transaction>): Int

    fun findByBookingDateBetween(
        from: LocalDate,
        to: LocalDate,
        accountIban: String? = null,
    ): List<Transaction>

    fun findNegativeByBookingDateBetween(
        from: LocalDate,
        to: LocalDate,
        accountIban: String? = null,
    ): List<Transaction>

    fun findExistingFingerprints(fingerprints: Collection<String>): Set<String>
}
