package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Category

interface CategoryRepository {
    fun findAll(organizationId: Long): List<Category>

    fun saveAllIfAbsent(
        categories: List<Category>,
        organizationId: Long,
    )

    fun findOrCreate(
        name: String,
        subcategory: String,
        group: String?,
        organizationId: Long,
    ): Category
}
