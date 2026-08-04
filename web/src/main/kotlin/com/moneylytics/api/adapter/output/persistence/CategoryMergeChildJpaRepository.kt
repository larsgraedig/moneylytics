package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryMergeChildJpaRepository : JpaRepository<CategoryMergeChildEntity, CategoryMergeChildId>
