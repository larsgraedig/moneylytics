package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryMergeTransactionJpaRepository : JpaRepository<CategoryMergeTransactionEntity, CategoryMergeTransactionId>
