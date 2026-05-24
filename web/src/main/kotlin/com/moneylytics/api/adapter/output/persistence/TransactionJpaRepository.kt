package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDate

interface TransactionJpaRepository : JpaRepository<TransactionEntity, Long> {
    fun findByBookingDateBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<TransactionEntity>

    fun findByBookingDateBetweenAndAmountLessThan(
        from: LocalDate,
        to: LocalDate,
        amount: BigDecimal,
    ): List<TransactionEntity>

    fun findByAccountIbanAndBookingDateBetween(
        iban: String,
        from: LocalDate,
        to: LocalDate,
    ): List<TransactionEntity>

    fun findByAccountIbanAndBookingDateBetweenAndAmountLessThan(
        iban: String,
        from: LocalDate,
        to: LocalDate,
        amount: BigDecimal,
    ): List<TransactionEntity>

    @Query("SELECT t.fingerprint FROM TransactionEntity t WHERE t.fingerprint IN :fingerprints")
    fun findExistingFingerprints(
        @Param("fingerprints") fingerprints: Collection<String>,
    ): List<String>
}
