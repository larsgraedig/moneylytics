package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate

@Component
class TransactionPersistenceAdapter(
    private val jpaRepository: TransactionJpaRepository,
    private val accountJpaRepository: AccountJpaRepository,
) : TransactionRepository {
    @Transactional
    override fun saveAll(transactions: List<Transaction>): Int {
        if (transactions.isEmpty()) return 0

        val withFingerprints = transactions.map { it to it.fingerprint() }
        val existing =
            jpaRepository
                .findExistingFingerprints(withFingerprints.map { it.second })
                .toHashSet()

        val newEntities =
            withFingerprints
                .filter { (_, fp) -> fp !in existing }
                .map { (tx, fp) -> tx.toEntity(fp) }

        jpaRepository.saveAll(newEntities)
        return newEntities.size
    }

    @Transactional(readOnly = true)
    override fun findByBookingDateBetween(
        from: LocalDate,
        to: LocalDate,
        accountIban: String?,
    ): List<Transaction> =
        if (accountIban != null) {
            jpaRepository.findByAccountIbanAndBookingDateBetween(accountIban, from, to)
        } else {
            jpaRepository.findByBookingDateBetween(from, to)
        }.map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findNegativeByBookingDateBetween(
        from: LocalDate,
        to: LocalDate,
        accountIban: String?,
    ): List<Transaction> =
        if (accountIban != null) {
            jpaRepository.findByAccountIbanAndBookingDateBetweenAndAmountLessThan(
                accountIban,
                from,
                to,
                BigDecimal.ZERO,
            )
        } else {
            jpaRepository.findByBookingDateBetweenAndAmountLessThan(from, to, BigDecimal.ZERO)
        }.map { it.toDomain() }

    private fun Transaction.toEntity(fingerprint: String): TransactionEntity {
        val account =
            accountJpaRepository.findByIban(accountIban)
                ?: error("Account not found for IBAN $accountIban — ensure accounts are created before importing transactions")
        return TransactionEntity(
            category = category,
            subcategory = subcategory,
            bookingDate = bookingDate,
            valueDate = valueDate,
            amount = amount,
            currency = currency,
            account = account,
            fingerprint = fingerprint,
        )
    }

    private fun TransactionEntity.toDomain() =
        Transaction(
            category = category,
            subcategory = subcategory,
            bookingDate = bookingDate,
            valueDate = valueDate,
            amount = amount,
            currency = currency,
            accountIban = account.iban,
        )

    private fun Transaction.fingerprint(): String {
        val raw = "$accountIban|$bookingDate|$valueDate|${amount.stripTrailingZeros().toPlainString()}|$currency"
        return MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
