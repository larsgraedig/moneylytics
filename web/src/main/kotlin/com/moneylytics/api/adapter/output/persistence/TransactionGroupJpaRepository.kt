package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TransactionGroupJpaRepository : JpaRepository<TransactionGroupEntity, Long> {
    fun findAllByUserId(userId: Long): List<TransactionGroupEntity>

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): TransactionGroupEntity?
}
