package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

data class CategoryMergeChildId(
    val mergeId: Long = 0,
    val childCategoryId: Long = 0,
) : Serializable

@Entity
@Table(name = "category_merge_child")
@IdClass(CategoryMergeChildId::class)
class CategoryMergeChildEntity(
    @Id
    @Column(name = "merge_id")
    val mergeId: Long,
    @Id
    @Column(name = "child_category_id")
    val childCategoryId: Long,
)
