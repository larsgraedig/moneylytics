package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDate

@Suppress("TooManyFunctions")
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

    @Query(
        "SELECT t FROM TransactionEntity t WHERE t.organization.id = :organizationId AND t.account.id = :accountId AND t.accountingDate BETWEEN :from AND :to AND t.excluded = false",
    )
    fun findByOrganizationIdAndAccountIdAndAccountingDateBetweenAndExcludedFalse(
        @Param("organizationId") organizationId: Long,
        @Param("accountId") accountId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<TransactionEntity>

    @Query(
        "SELECT t FROM TransactionEntity t WHERE t.organization.id = :organizationId AND t.account.id = :accountId AND t.accountingDate BETWEEN :from AND :to AND t.amount < :amount AND t.excluded = false",
    )
    fun findByOrganizationIdAndAccountIdAndAccountingDateBetweenAndAmountLessThanAndExcludedFalse(
        @Param("organizationId") organizationId: Long,
        @Param("accountId") accountId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("amount") amount: BigDecimal,
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
        "SELECT t.fingerprint FROM TransactionEntity t WHERE t.fingerprint IN :fingerprints AND t.organization.id = :organizationId AND t.excluded = false",
    )
    fun findExistingFingerprints(
        @Param("fingerprints") fingerprints: Collection<String>,
        @Param("organizationId") organizationId: Long,
    ): List<String>

    @Query(
        "SELECT t.fingerprint FROM TransactionEntity t WHERE t.fingerprint IN :fingerprints AND t.organization.id = :organizationId",
    )
    fun findAllExistingFingerprints(
        @Param("fingerprints") fingerprints: Collection<String>,
        @Param("organizationId") organizationId: Long,
    ): List<String>

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.importId = :importId WHERE t.id IN :ids")
    fun setImportId(
        @Param("importId") importId: Long,
        @Param("ids") ids: Collection<Long>,
    )

    @Query(
        "SELECT t.id FROM TransactionEntity t WHERE t.fingerprint IN :fingerprints AND t.organization.id = :organizationId AND t.excluded = true",
    )
    fun findExcludedIdsByFingerprints(
        @Param("fingerprints") fingerprints: Collection<String>,
        @Param("organizationId") organizationId: Long,
    ): List<Long>

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.excluded = false WHERE t.id IN :ids")
    fun reactivateByIds(
        @Param("ids") ids: Collection<Long>,
    )

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.excluded = true WHERE t.importId = :importId AND t.organization.id = :orgId")
    fun excludeByImportId(
        @Param("importId") importId: Long,
        @Param("orgId") orgId: Long,
    )

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.excluded = true WHERE t.id IN :ids AND t.organization.id = :orgId")
    fun excludeByIds(
        @Param("ids") ids: Collection<Long>,
        @Param("orgId") orgId: Long,
    )

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.importFileId = :fileId WHERE t.id IN :ids")
    fun setImportFileId(
        @Param("fileId") fileId: Long,
        @Param("ids") ids: Collection<Long>,
    )

    @Query("SELECT t.id FROM TransactionEntity t WHERE t.fingerprint IN :fps AND t.organization.id = :orgId")
    fun findIdsByFingerprints(
        @Param("fps") fps: Collection<String>,
        @Param("orgId") orgId: Long,
    ): List<Long>

    @Query("SELECT t.id FROM TransactionEntity t WHERE t.importFileId = :importFileId")
    fun findIdsByImportFileId(
        @Param("importFileId") importFileId: Long,
    ): List<Long>

    @Query("SELECT t.id FROM TransactionEntity t WHERE t.importId = :importId")
    fun findIdsByImportId(
        @Param("importId") importId: Long,
    ): List<Long>

    @Query(
        "SELECT t FROM TransactionEntity t WHERE t.importId = :importId AND t.organization.id = :organizationId ORDER BY t.bookingDate ASC",
    )
    fun findByImportIdAndOrganizationId(
        @Param("importId") importId: Long,
        @Param("organizationId") organizationId: Long,
    ): List<TransactionEntity>

    @Query(
        "SELECT t FROM TransactionEntity t WHERE t.importFileId = :fileId AND t.organization.id = :organizationId ORDER BY t.bookingDate ASC",
    )
    fun findByImportFileIdAndOrganizationId(
        @Param("fileId") fileId: Long,
        @Param("organizationId") organizationId: Long,
    ): List<TransactionEntity>

    fun existsByParentIdAndExcludedFalse(parentId: Long): Boolean

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

    @Query(
        """SELECT t.category.id, COUNT(t) FROM TransactionEntity t
        WHERE t.organization.id = :organizationId
        AND t.excluded = false
        AND t.category IS NOT NULL
        AND (:accountId IS NULL OR t.account.id = :accountId)
        GROUP BY t.category.id""",
    )
    fun countByCategoryGrouped(
        @Param("organizationId") organizationId: Long,
        @Param("accountId") accountId: Long?,
    ): List<Array<out Any?>>

    @Query(
        """SELECT t.category.id, COUNT(t) FROM TransactionEntity t
        WHERE t.organization.id = :organizationId
        AND t.excluded = false
        AND t.category IS NOT NULL
        AND (:accountId IS NULL OR t.account.id = :accountId)
        AND t.accountingDate BETWEEN :from AND :to
        GROUP BY t.category.id""",
    )
    fun countByCategoryGroupedInPeriod(
        @Param("organizationId") organizationId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("accountId") accountId: Long?,
    ): List<Array<out Any?>>

    @Query(
        "SELECT t.id FROM TransactionEntity t WHERE t.category.id = :categoryId AND t.organization.id = :organizationId",
    )
    fun findIdsByCategoryId(
        @Param("categoryId") categoryId: Long,
        @Param("organizationId") organizationId: Long,
    ): List<Long>

    @Modifying
    @Query(
        "UPDATE TransactionEntity t SET t.category.id = :targetId WHERE t.category.id = :sourceId AND t.organization.id = :organizationId",
    )
    fun moveByCategoryId(
        @Param("sourceId") sourceId: Long,
        @Param("targetId") targetId: Long,
        @Param("organizationId") organizationId: Long,
    )

    @Modifying
    @Query(
        "UPDATE TransactionEntity t SET t.category.id = :targetId WHERE t.id IN :ids AND t.organization.id = :organizationId",
    )
    fun moveBulkToCategory(
        @Param("ids") ids: Collection<Long>,
        @Param("targetId") targetId: Long,
        @Param("organizationId") organizationId: Long,
    )

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.suggestedCategoryId = :categoryId WHERE t.id = :id")
    fun updateSuggestedCategoryId(
        @Param("id") id: Long,
        @Param("categoryId") categoryId: Long,
    )

    @Query(
        """SELECT t.purpose, t.counterpartyName, t.counterpartyIban, t.category.id, t.amount
        FROM TransactionEntity t
        WHERE t.organization.id = :organizationId
        AND t.category IS NOT NULL
        AND t.excluded = false
        AND t.parentId IS NULL""",
    )
    fun findCategorizedForBootstrap(
        @Param("organizationId") organizationId: Long,
    ): List<Array<out Any?>>
}
