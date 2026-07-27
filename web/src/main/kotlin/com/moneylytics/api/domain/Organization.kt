package com.moneylytics.api.domain

data class Organization(
    val id: Long,
    val name: String,
    val logoUrl: String? = null,
)
