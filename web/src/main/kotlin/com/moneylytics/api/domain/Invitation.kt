package com.moneylytics.api.domain

import java.time.Instant

data class Invitation(
    val id: Long,
    val organizationId: Long,
    val organizationName: String,
    val email: String,
    val role: OrgRole,
    val token: String,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
)
