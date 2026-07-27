package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "recurring_classifier_class_count")
class RecurringClassifierClassCountEntity(
    @Column(name = "organization_id", nullable = false)
    val organizationId: Long,
    @Column(nullable = false, length = 50)
    val type: String,
    @Column(nullable = false)
    var count: Int = 0,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
