package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface RecurringClassifierTokenCountJpaRepository : JpaRepository<RecurringClassifierTokenCountEntity, Long> {
    fun findByUserId(userId: Long): List<RecurringClassifierTokenCountEntity>

    fun findByUserIdAndTypeAndToken(
        userId: Long,
        type: String,
        token: String,
    ): RecurringClassifierTokenCountEntity?

    fun existsByUserId(userId: Long): Boolean
}

interface RecurringClassifierClassCountJpaRepository : JpaRepository<RecurringClassifierClassCountEntity, Long> {
    fun findByUserId(userId: Long): List<RecurringClassifierClassCountEntity>

    fun findByUserIdAndType(
        userId: Long,
        type: String,
    ): RecurringClassifierClassCountEntity?
}
