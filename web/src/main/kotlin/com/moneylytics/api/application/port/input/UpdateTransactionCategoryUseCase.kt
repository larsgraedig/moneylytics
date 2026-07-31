package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

interface UpdateTransactionCategoryUseCase {
    fun updateCategory(
        id: Long,
        organizationId: Long,
        categoryId: Long?,
    ): Transaction?
}
