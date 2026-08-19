package com.moneylytics.api.domain

data class Tier(
    val id: Long,
    val name: String,
    val description: String?,
    val active: Boolean,
    val isDefault: Boolean,
)
