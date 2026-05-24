package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryJpaRepository : JpaRepository<CategoryEntity, Long> {
    fun existsByNameAndSubcategory(
        name: String,
        subcategory: String,
    ): Boolean
}
