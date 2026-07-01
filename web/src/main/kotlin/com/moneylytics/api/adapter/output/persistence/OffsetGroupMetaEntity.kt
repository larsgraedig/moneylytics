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
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "transaction_offset_group_meta",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "group_key"])],
)
class OffsetGroupMetaEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @Column(nullable = false, name = "group_key")
    val groupKey: Long,
    @Column(nullable = true, length = 255)
    var name: String? = null,
    @Column(nullable = true, columnDefinition = "TEXT")
    var comment: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
