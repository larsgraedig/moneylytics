package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

data class BulkCategoryUpdate(
    val id: Long,
    val categoryId: Long?,
)

interface BulkUpdateTransactionCategoryUseCase {
    fun bulkUpdateCategory(
        updates: List<BulkCategoryUpdate>,
        organizationId: Long,
    ): List<Transaction>
}
