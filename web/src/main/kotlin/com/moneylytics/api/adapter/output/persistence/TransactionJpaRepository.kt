package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDate

interface TransactionJpaRepository : JpaRepository<TransactionEntity, Long> {
    fun findByOrganizationIdAndAccountingDateBetweenAndExcludedFalse(
        organizationId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<TransactionEntity>

    fun findByOrganizationIdAndAccountingDateBetweenAndAmountLessThanAndExcludedFalse(
        organizationId: Long,
        from: LocalDate,
        to: LocalDate,
        amount: BigDecimal,
    ): List<TransactionEntity>

    fun findByOrganizationIdAndAccountIbanAndAccountingDateBetweenAndExcludedFalse(
        organizationId: Long,
        iban: String,
        from: LocalDate,
        to: LocalDate,
    ): List<TransactionEntity>

    fun findByOrganizationIdAndAccountIbanAndAccountingDateBetweenAndAmountLessThanAndExcludedFalse(
        organizationId: Long,
        iban: String,
        from: LocalDate,
        to: LocalDate,
        amount: BigDecimal,
    ): List<TransactionEntity>

    fun findByParentIdAndOrganizationId(
        parentId: Long,
        organizationId: Long,
    ): List<TransactionEntity>

    @Query("SELECT t FROM TransactionEntity t WHERE t.id = :id AND t.organization.id = :organizationId")
    fun findByIdAndOrganizationId(
        @Param("id") id: Long,
        @Param("organizationId") organizationId: Long,
    ): TransactionEntity?

    @Query(
        "SELECT t.fingerprint FROM TransactionEntity t WHERE t.fingerprint IN :fingerprints AND t.organization.id = :organizationId",
    )
    fun findExistingFingerprints(
        @Param("fingerprints") fingerprints: Collection<String>,
        @Param("organizationId") organizationId: Long,
    ): List<String>

    @Query("SELECT t FROM TransactionEntity t WHERE t.id IN :ids AND t.organization.id = :organizationId")
    fun findByIdsAndOrganizationId(
        @Param("ids") ids: Collection<Long>,
        @Param("organizationId") organizationId: Long,
    ): List<TransactionEntity>

    @Query("SELECT t FROM TransactionEntity t WHERE t.fingerprint = :fingerprint AND t.organization.id = :organizationId")
    fun findByFingerprintAndOrganizationId(
        @Param("fingerprint") fingerprint: String,
        @Param("organizationId") organizationId: Long,
    ): TransactionEntity?

    @Query(
        "SELECT t.account.iban, MAX(t.accountingDate) FROM TransactionEntity t WHERE t.organization.id = :organizationId GROUP BY t.account.iban",
    )
    fun findLatestDatePerIban(
        @Param("organizationId") organizationId: Long,
    ): List<Array<out Any?>>
}
