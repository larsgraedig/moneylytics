package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface TransactionJpaRepository : JpaRepository<TransactionEntity, Long> {
    fun findByBookingDateBetween(from: LocalDate, to: LocalDate): List<TransactionEntity>
}
