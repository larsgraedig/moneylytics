package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.OrgRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

data class OrganizationMemberId(
    val organization: Long = 0,
    val user: Long = 0,
) : Serializable

@Entity
@Table(name = "organization_member")
@IdClass(OrganizationMemberId::class)
class OrganizationMemberEntity(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    val organization: OrganizationEntity,
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: OrgRole = OrgRole.MEMBER,
    @Column(nullable = false)
    val joinedAt: Instant = Instant.now(),
)
