package com.moneylytics.api.domain

data class TransactionGroup(
    val id: Long,
    val userId: Long,
    val name: String?,
    val comment: String?,
)
