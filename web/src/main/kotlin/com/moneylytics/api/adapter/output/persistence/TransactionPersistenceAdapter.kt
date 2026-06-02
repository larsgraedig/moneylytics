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
    private val userJpaRepository: UserJpaRepository,
) : TransactionRepository {
    @Transactional
    override fun saveAll(
        transactions: List<Transaction>,
        userId: Long,
    ): Int {
        if (transactions.isEmpty()) return 0

        val withFingerprints = assignFingerprints(transactions)
        val existing =
            jpaRepository
                .findExistingFingerprints(withFingerprints.map { it.second }, userId)
                .toHashSet()

        val newEntities =
            withFingerprints
                .filter { (_, fp) -> fp !in existing }
                .map { (tx, fp) -> tx.toEntity(fp, userId) }

        jpaRepository.saveAll(newEntities)
        return newEntities.size
    }

    private fun assignFingerprints(transactions: List<Transaction>): List<Pair<Transaction, String>> {
        val counts = mutableMapOf<String, Int>()
        return transactions.map { tx ->
            val raw = tx.fingerprintRaw()
            val n = counts.merge(raw, 1, Int::plus)!!
            tx to sha256(if (n == 1) raw else "$raw:${n - 1}")
        }
    }

    @Transactional(readOnly = true)
    override fun findExistingFingerprints(
        fingerprints: Collection<String>,
        userId: Long,
    ): Set<String> = jpaRepository.findExistingFingerprints(fingerprints, userId).toHashSet()

    @Transactional(readOnly = true)
    override fun findByBookingDateBetween(
        from: LocalDate,
        to: LocalDate,
        userId: Long,
        accountIban: String?,
    ): List<Transaction> =
        if (accountIban != null) {
            jpaRepository.findByUserIdAndAccountIbanAndBookingDateBetween(userId, accountIban, from, to)
        } else {
            jpaRepository.findByUserIdAndBookingDateBetween(userId, from, to)
        }.map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findNegativeByBookingDateBetween(
        from: LocalDate,
        to: LocalDate,
        userId: Long,
        accountIban: String?,
    ): List<Transaction> =
        if (accountIban != null) {
            jpaRepository.findByUserIdAndAccountIbanAndBookingDateBetweenAndAmountLessThan(
                userId,
                accountIban,
                from,
                to,
                BigDecimal.ZERO,
            )
        } else {
            jpaRepository.findByUserIdAndBookingDateBetweenAndAmountLessThan(userId, from, to, BigDecimal.ZERO)
        }.map { it.toDomain() }

    private fun Transaction.toEntity(
        fingerprint: String,
        userId: Long,
    ): TransactionEntity {
        val account =
            accountJpaRepository.findByIbanAndUserId(accountIban, userId)
                ?: error(
                    "Account not found for IBAN $accountIban and userId $userId — ensure accounts are created before importing transactions",
                )
        return TransactionEntity(
            category = category,
            subcategory = subcategory,
            bookingDate = bookingDate,
            valueDate = valueDate,
            amount = amount,
            currency = currency,
            account = account,
            fingerprint = fingerprint,
            user = userJpaRepository.getReferenceById(userId),
        )
    }

    @Transactional
    override fun updateCategory(
        id: Long,
        userId: Long,
        category: String,
        subcategory: String,
    ): Transaction? {
        val entity = jpaRepository.findByIdAndUserId(id, userId) ?: return null
        entity.category = category
        entity.subcategory = subcategory
        return jpaRepository.save(entity).toDomain()
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
            id = id,
        )

    private fun Transaction.fingerprintRaw() =
        "$accountIban|$bookingDate|$valueDate|${amount.stripTrailingZeros().toPlainString()}|$currency"

    private fun sha256(raw: String) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
