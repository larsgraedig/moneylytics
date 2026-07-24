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
@Table(name = "recurring_series_member")
class RecurringSeriesMemberEntity(
    @Column(nullable = false)
    val seriesId: Long,
    @Column(nullable = false)
    val transactionId: Long,
    @Column(nullable = false)
    val occurredOn: LocalDate,
    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
