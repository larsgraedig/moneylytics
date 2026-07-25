package com.moneylytics.api.domain

data class User(
    val id: Long,
    val externalId: String,
    val passwordHash: String?,
    val role: Role = Role.USER,
)
