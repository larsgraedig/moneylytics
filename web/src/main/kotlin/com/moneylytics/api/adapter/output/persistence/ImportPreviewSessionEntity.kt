package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "import_preview_session")
class ImportPreviewSessionEntity(
    @Id
    val id: UUID,
    @Column(nullable = false, columnDefinition = "TEXT")
    val rowsJson: String,
    @Column(nullable = false)
    val expiresAt: LocalDateTime,
)
