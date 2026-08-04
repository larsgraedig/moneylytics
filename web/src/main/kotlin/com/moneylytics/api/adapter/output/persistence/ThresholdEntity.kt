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
@Table(name = "threshold")
class ThresholdEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    val organization: OrganizationEntity,
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "category_id", nullable = true)
    val categoryEntity: CategoryEntity? = null,
    @Column(nullable = true)
    val category: String? = null,
    val subcategory: String? = null,
    @Column(nullable = true, name = "group_name")
    val groupName: String? = null,
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

fun CategoryEntity.buildPath(): List<String> {
    val path = mutableListOf<String>()
    var current: CategoryEntity? = this
    while (current != null) {
        path.add(0, current.name)
        current = current.parent
    }
    return path
}
