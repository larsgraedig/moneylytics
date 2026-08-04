package com.moneylytics.api.domain

import java.time.Instant

data class CategoryMerge(
    val id: Long,
    val sourceCategoryId: Long?,
    val sourceName: String,
    val sourceParentId: Long?,
    val targetCategoryId: Long,
    val mergedAt: Instant,
    val transactionCount: Int,
    val childCount: Int,
    val reverted: Boolean,
)

data class NewCategoryMerge(
    val sourceCategoryId: Long?,
    val sourceName: String,
    val sourceParentId: Long?,
    val targetCategoryId: Long,
    val organizationId: Long,
    val transactionIds: List<Long>,
    val childCategoryIds: List<Long>,
)
