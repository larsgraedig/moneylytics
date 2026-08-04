package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.CategoryMerge
import com.moneylytics.api.domain.NewCategoryMerge

interface CategoryMergeRepository {
    fun save(merge: NewCategoryMerge): CategoryMerge

    fun findActiveByOrganization(organizationId: Long): List<CategoryMerge>

    fun findById(
        mergeId: Long,
        organizationId: Long,
    ): CategoryMerge?

    fun getTransactionIds(mergeId: Long): List<Long>

    fun getChildCategoryIds(mergeId: Long): List<Long>

    fun markReverted(mergeId: Long)
}
