package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.OrgRole
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
import java.time.Instant

@Entity
@Table(name = "organization_invitations")
class InvitationEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    val organization: OrganizationEntity,
    @Column(nullable = false)
    val email: String,
    @Column(nullable = false, unique = true, length = 64)
    val token: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: OrgRole = OrgRole.MEMBER,
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by_user_id", nullable = true)
    val createdBy: UserEntity? = null,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    val expiresAt: Instant,
    @Column(nullable = true)
    var acceptedAt: Instant? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "accepted_by_user_id", nullable = true)
    var acceptedBy: UserEntity? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
