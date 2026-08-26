package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TransactionImportFileJpaRepository : JpaRepository<TransactionImportFileEntity, Long> {
    fun findByImportId(importId: Long): List<TransactionImportFileEntity>

    fun findByImportIdIn(importIds: List<Long>): List<TransactionImportFileEntity>

    @Query("SELECT f FROM TransactionImportFileEntity f WHERE f.id = :id AND f.import.id = :importId")
    fun findByIdAndImportId(
        @Param("id") id: Long,
        @Param("importId") importId: Long,
    ): TransactionImportFileEntity?

    @Query("SELECT t.id FROM TransactionEntity t WHERE t.importFileId = :importFileId")
    fun findTransactionIdsByImportFileId(
        @Param("importFileId") importFileId: Long,
    ): List<Long>

    @Query(
        "SELECT CASE WHEN COUNT(f) = 0 THEN true ELSE false END " +
            "FROM TransactionImportFileEntity f WHERE f.import.id = :importId AND f.status IN ('ACTIVE', 'PARTIALLY_REJECTED')",
    )
    fun allFilesFullyRejected(
        @Param("importId") importId: Long,
    ): Boolean

    @Query(
        "SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END " +
            "FROM TransactionImportFileEntity f WHERE f.import.id = :importId AND f.status IN ('REJECTED', 'PARTIALLY_REJECTED')",
    )
    fun anyFileRejectedOrPartial(
        @Param("importId") importId: Long,
    ): Boolean

    @Modifying
    @Query("UPDATE TransactionImportFileEntity f SET f.status = :status WHERE f.id = :id")
    fun updateStatus(
        @Param("id") id: Long,
        @Param("status") status: String,
    )
}
