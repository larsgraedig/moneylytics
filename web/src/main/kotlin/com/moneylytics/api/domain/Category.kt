package com.moneylytics.api.domain

data class Category(
    val id: Long? = null,
    val name: String,
    val parentId: Long? = null,
)
