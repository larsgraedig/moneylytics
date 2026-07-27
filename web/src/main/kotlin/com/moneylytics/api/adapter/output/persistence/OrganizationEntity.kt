package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "organizations")
class OrganizationEntity(
    @Column(nullable = false)
    val name: String,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(nullable = true, name = "logo_data")
    var logoData: ByteArray? = null,
    @Column(nullable = true, name = "logo_content_type", length = 100)
    var logoContentType: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
