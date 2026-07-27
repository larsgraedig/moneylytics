package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.RecurringSyncTrigger
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "recurring_sync_log")
class RecurringSyncLogEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    val organization: OrganizationEntity,
    @Column(nullable = false)
    val ranAt: Instant,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "triggered_by")
    val triggeredBy: RecurringSyncTrigger,
    @Column(nullable = false)
    val seriesUpdatedCount: Int,
    @Column(nullable = false)
    val transactionsLinkedCount: Int,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)

@Entity
@Table(name = "recurring_sync_log_entry")
class RecurringSyncLogEntryEntity(
    @Column(nullable = false)
    val logId: Long,
    @Column(nullable = true)
    val seriesId: Long?,
    @Column(nullable = false)
    val seriesLabel: String,
    @Column(nullable = false)
    val transactionId: Long,
    @Column(nullable = false)
    val bookingDate: java.time.LocalDate,
    @Column(nullable = false, precision = 19, scale = 4)
    val amount: java.math.BigDecimal,
    @Column(nullable = true)
    val counterpartyName: String?,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
