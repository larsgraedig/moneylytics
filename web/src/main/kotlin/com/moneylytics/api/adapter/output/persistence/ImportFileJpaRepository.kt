package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ImportFileJpaRepository : JpaRepository<ImportFileEntity, Long> {
    fun findByImportId(importId: Long): List<ImportFileEntity>

    fun findByImportIdIn(importIds: List<Long>): List<ImportFileEntity>

    @Query("SELECT f FROM ImportFileEntity f WHERE f.id = :id AND f.import.id = :importId")
    fun findByIdAndImportId(
        @Param("id") id: Long,
        @Param("importId") importId: Long,
    ): ImportFileEntity?

    @Query("SELECT t.id FROM TransactionEntity t WHERE t.importFileId = :importFileId")
    fun findTransactionIdsByImportFileId(
        @Param("importFileId") importFileId: Long,
    ): List<Long>

    @Query(
        "SELECT CASE WHEN COUNT(f) = 0 THEN true ELSE false END " +
            "FROM ImportFileEntity f WHERE f.import.id = :importId AND f.status = 'ACTIVE'",
    )
    fun allFilesRejected(
        @Param("importId") importId: Long,
    ): Boolean

    @Modifying
    @Query("UPDATE ImportFileEntity f SET f.status = :status WHERE f.id = :id")
    fun updateStatus(
        @Param("id") id: Long,
        @Param("status") status: String,
    )
}
