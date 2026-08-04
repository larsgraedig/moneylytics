package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Category

interface CategoryRepository {
    fun findAll(organizationId: Long): List<Category>

    fun findOrCreate(
        path: List<String>,
        organizationId: Long,
    ): Category

    fun delete(
        id: Long,
        organizationId: Long,
    )

    fun move(
        id: Long,
        newParentId: Long?,
        organizationId: Long,
    )

    fun rename(
        id: Long,
        newName: String,
        organizationId: Long,
    )
}
