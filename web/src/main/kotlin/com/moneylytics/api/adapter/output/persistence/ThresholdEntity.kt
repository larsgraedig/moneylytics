package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.ThresholdPeriod
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

@Entity
@Table(name = "thresholds")
class ThresholdEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @Column(nullable = false)
    val category: String,
    val subcategory: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val period: ThresholdPeriod,
    @Column(precision = 19, scale = 2)
    var notice: BigDecimal? = null,
    @Column(precision = 19, scale = 2)
    var warning: BigDecimal? = null,
    @Column(precision = 19, scale = 2)
    var critical: BigDecimal? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
