package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Category

interface CategoryRepository {
    fun findAll(userId: Long): List<Category>

    fun saveAllIfAbsent(
        categories: List<Category>,
        userId: Long,
    )
}
