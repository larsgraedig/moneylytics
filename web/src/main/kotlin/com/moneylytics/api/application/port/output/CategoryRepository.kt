package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Category

interface CategoryRepository {
    fun findAll(): List<Category>

    fun saveAllIfAbsent(categories: List<Category>)
}
