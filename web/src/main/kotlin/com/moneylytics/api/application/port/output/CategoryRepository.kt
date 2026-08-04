package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Category

interface CategoryRepository {
    fun findAll(organizationId: Long): List<Category>

    fun findOrCreate(
        path: List<String>,
        organizationId: Long,
    ): Category
}
