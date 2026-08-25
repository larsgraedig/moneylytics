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
@Table(name = "transaction_import")
class TransactionImportEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    val organization: OrganizationEntity,
    @Column(nullable = false)
    val importedAt: Instant,
    @Column(nullable = false, length = 500)
    val filename: String,
    @Column(nullable = false, length = 64)
    val checksum: String,
    @Column(nullable = false, length = 10, name = "file_type")
    val fileType: String,
    @Column(nullable = false, name = "transaction_count")
    val transactionCount: Int,
    @Column(nullable = false, length = 10)
    var status: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
