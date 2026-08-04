package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "category_merge")
class CategoryMergeEntity(
    @Column(name = "source_category_id", nullable = true)
    var sourceCategoryId: Long?,
    @Column(name = "source_name", nullable = false)
    val sourceName: String,
    @Column(name = "source_parent_id", nullable = true)
    val sourceParentId: Long?,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_category_id", nullable = false)
    val targetCategory: CategoryEntity,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    val organization: OrganizationEntity,
    @Column(name = "merged_at", nullable = false)
    val mergedAt: Instant = Instant.now(),
    @Column(name = "reverted_at", nullable = true)
    var revertedAt: Instant? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
