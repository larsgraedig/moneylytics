package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringType
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
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "recurring_series")
class RecurringSeriesEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    val organization: OrganizationEntity,
    @Column(nullable = false)
    var label: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: RecurringType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var direction: RecurrenceDirection,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var cadence: RecurrenceCadence,
    @Column(nullable = false)
    var intervalDays: Int,
    @Column(nullable = false, precision = 19, scale = 4)
    var expectedAmount: BigDecimal,
    @Column(nullable = false)
    var amountVariable: Boolean,
    @Column(nullable = false, length = 3)
    var currency: String,
    @Column(nullable = false)
    var accountIban: String,
    @Column(nullable = false)
    var firstSeen: LocalDate,
    @Column(nullable = false)
    var lastSeen: LocalDate,
    @Column(nullable = false)
    var occurrenceCount: Int,
    @Column(nullable = false)
    var nextExpectedDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RecurrenceStatus,
    @Column(nullable = false, length = 512)
    var fingerprint: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
