package com.moneylytics.api.domain

data class Category(
    val name: String,
    val subcategory: String,
    val group: String? = null,
)
