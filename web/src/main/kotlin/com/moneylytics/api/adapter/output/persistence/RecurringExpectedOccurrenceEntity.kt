package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "recurring_expected_occurrence")
class RecurringExpectedOccurrenceEntity(
    @Column(nullable = false)
    var seriesId: Long,
    @Column(nullable = false)
    var expectedDate: LocalDate,
    @Column(nullable = false, precision = 19, scale = 4)
    var expectedAmount: BigDecimal,
    @Column
    var matchedTransactionId: Long? = null,
    @Column
    var matchedDate: LocalDate? = null,
    @Column(precision = 19, scale = 4)
    var matchedAmount: BigDecimal? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
