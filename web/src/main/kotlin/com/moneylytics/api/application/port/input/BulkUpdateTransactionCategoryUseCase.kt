package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

data class BulkCategoryUpdate(
    val id: Long,
    val category: String,
    val subcategory: String,
    val categoryGroup: String? = null,
)

interface BulkUpdateTransactionCategoryUseCase {
    fun bulkUpdateCategory(
        updates: List<BulkCategoryUpdate>,
        organizationId: Long,
    ): List<Transaction>
}
