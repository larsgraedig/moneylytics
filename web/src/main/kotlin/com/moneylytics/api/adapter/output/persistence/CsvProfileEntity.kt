package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "csv_profile")
class CsvProfileEntity(
    @Column(nullable = false)
    val organizationId: Long,
    @Column(nullable = false, columnDefinition = "TEXT")
    val fingerprint: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var mappingJson: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
