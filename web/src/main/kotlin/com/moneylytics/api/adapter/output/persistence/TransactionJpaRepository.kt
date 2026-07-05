package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDate

interface TransactionJpaRepository : JpaRepository<TransactionEntity, Long> {
    fun findByUserIdAndAccountingDateBetween(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<TransactionEntity>

    fun findByUserIdAndAccountingDateBetweenAndAmountLessThan(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
        amount: BigDecimal,
    ): List<TransactionEntity>

    fun findByUserIdAndAccountIbanAndAccountingDateBetween(
        userId: Long,
        iban: String,
        from: LocalDate,
        to: LocalDate,
    ): List<TransactionEntity>

    fun findByUserIdAndAccountIbanAndAccountingDateBetweenAndAmountLessThan(
        userId: Long,
        iban: String,
        from: LocalDate,
        to: LocalDate,
        amount: BigDecimal,
    ): List<TransactionEntity>

    @Query("SELECT t FROM TransactionEntity t WHERE t.id = :id AND t.user.id = :userId")
    fun findByIdAndUserId(
        @Param("id") id: Long,
        @Param("userId") userId: Long,
    ): TransactionEntity?

    @Query("SELECT t.fingerprint FROM TransactionEntity t WHERE t.fingerprint IN :fingerprints AND t.user.id = :userId")
    fun findExistingFingerprints(
        @Param("fingerprints") fingerprints: Collection<String>,
        @Param("userId") userId: Long,
    ): List<String>

    @Query("SELECT t FROM TransactionEntity t WHERE t.id IN :ids AND t.user.id = :userId")
    fun findByIdsAndUserId(
        @Param("ids") ids: Collection<Long>,
        @Param("userId") userId: Long,
    ): List<TransactionEntity>

    @Query("SELECT t FROM TransactionEntity t WHERE t.fingerprint = :fingerprint AND t.user.id = :userId")
    fun findByFingerprintAndUserId(
        @Param("fingerprint") fingerprint: String,
        @Param("userId") userId: Long,
    ): TransactionEntity?

    @Query("SELECT t.account.iban, MAX(t.accountingDate) FROM TransactionEntity t WHERE t.user.id = :userId GROUP BY t.account.iban")
    fun findLatestDatePerIban(@Param("userId") userId: Long): List<Array<out Any?>>
}
