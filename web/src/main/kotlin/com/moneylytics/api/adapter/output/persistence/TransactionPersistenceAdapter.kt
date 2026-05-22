package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class TransactionPersistenceAdapter(
    private val jpaRepository: TransactionJpaRepository,
) : TransactionRepository {

    @Transactional
    override fun saveAll(transactions: List<Transaction>): Int {
        val entities = transactions.map { it.toEntity() }
        jpaRepository.saveAll(entities)
        return entities.size
    }

    override fun findByBookingDateBetween(from: LocalDate, to: LocalDate): List<Transaction> =
        jpaRepository.findByBookingDateBetween(from, to).map { it.toDomain() }

    private fun Transaction.toEntity() = TransactionEntity(
        category = category,
        subcategory = subcategory,
        bookingDate = bookingDate,
        valueDate = valueDate,
        amount = amount,
        currency = currency,
    )

    private fun TransactionEntity.toDomain() = Transaction(
        category = category,
        subcategory = subcategory,
        bookingDate = bookingDate,
        valueDate = valueDate,
        amount = amount,
        currency = currency,
    )
}
