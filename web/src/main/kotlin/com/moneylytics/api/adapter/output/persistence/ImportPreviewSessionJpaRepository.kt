package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface ImportPreviewSessionJpaRepository : JpaRepository<ImportPreviewSessionEntity, UUID> {
    fun deleteByExpiresAtBefore(cutoff: LocalDateTime): Int
}
