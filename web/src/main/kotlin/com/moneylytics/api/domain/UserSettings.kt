package com.moneylytics.api.domain

data class UserSettings(
    val defaultAccountIban: String?,
    val language: String?,
    val transactionsColumnOrder: List<String>?,
)
