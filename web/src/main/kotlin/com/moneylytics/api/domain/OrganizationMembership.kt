package com.moneylytics.api.domain

data class OrganizationMembership(
    val organization: Organization,
    val userId: Long,
    val email: String,
    val role: OrgRole,
)
