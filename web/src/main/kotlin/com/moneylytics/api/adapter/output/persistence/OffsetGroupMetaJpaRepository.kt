package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface OffsetGroupMetaJpaRepository : JpaRepository<OffsetGroupMetaEntity, Long> {
    fun findAllByUserId(userId: Long): List<OffsetGroupMetaEntity>

    fun findByUserIdAndGroupKey(userId: Long, groupKey: Long): OffsetGroupMetaEntity?
}
