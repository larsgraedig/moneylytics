package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "recurring_expected_slot")
class RecurringExpectedSlotEntity(
    @Column(nullable = false)
    val seriesId: Long,
    @Column(nullable = false)
    val expectedDate: LocalDate,
    @Column(nullable = false)
    val transactionId: Long,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
