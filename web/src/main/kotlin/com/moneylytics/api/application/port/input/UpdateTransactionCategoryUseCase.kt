package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

interface UpdateTransactionCategoryUseCase {
    fun updateCategory(
        id: Long,
        userId: Long,
        category: String,
        subcategory: String,
    ): Transaction?
}
