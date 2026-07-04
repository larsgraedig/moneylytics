package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import com.moneylytics.api.domain.TransactionOffsetLink
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
    private val offsetJpaRepository: TransactionOffsetJpaRepository,
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
    override fun findByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        userId: Long,
        accountIban: String?,
    ): List<Transaction> {
        val entities =
            if (accountIban != null) {
                jpaRepository.findByUserIdAndAccountIbanAndAccountingDateBetween(userId, accountIban, from, to)
            } else {
                jpaRepository.findByUserIdAndAccountingDateBetween(userId, from, to)
            }
        return enrichWithOffsetLinks(entities)
    }

    @Transactional(readOnly = true)
    override fun findNegativeByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        userId: Long,
        accountIban: String?,
    ): List<Transaction> {
        val entities =
            if (accountIban != null) {
                jpaRepository.findByUserIdAndAccountIbanAndAccountingDateBetweenAndAmountLessThan(
                    userId,
                    accountIban,
                    from,
                    to,
                    BigDecimal.ZERO,
                )
            } else {
                jpaRepository.findByUserIdAndAccountingDateBetweenAndAmountLessThan(userId, from, to, BigDecimal.ZERO)
            }
        return enrichWithOffsetLinks(entities)
    }

    @Transactional
    override fun updateAccountingDate(
        id: Long,
        userId: Long,
        accountingDate: LocalDate,
    ): Transaction? {
        val entity = jpaRepository.findByIdAndUserId(id, userId) ?: return null
        entity.accountingDate = accountingDate
        return enrichWithOffsetLinks(listOf(jpaRepository.save(entity))).first()
    }

    @Transactional(readOnly = true)
    override fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Transaction? {
        val entity = jpaRepository.findByIdAndUserId(id, userId) ?: return null
        return enrichWithOffsetLinks(listOf(entity)).first()
    }

    @Transactional
    override fun updateCategory(
        id: Long,
        userId: Long,
        category: String,
        subcategory: String,
        categoryGroup: String?,
    ): Transaction? {
        val entity = jpaRepository.findByIdAndUserId(id, userId) ?: return null
        entity.category = category.takeIf { it.isNotBlank() }
        entity.subcategory = subcategory.takeIf { it.isNotBlank() }
        entity.categoryGroup = categoryGroup?.takeIf { it.isNotBlank() }
        return enrichWithOffsetLinks(listOf(jpaRepository.save(entity))).first()
    }

    @Transactional
    override fun updateComment(
        id: Long,
        userId: Long,
        comment: String?,
    ): Transaction? {
        val entity = jpaRepository.findByIdAndUserId(id, userId) ?: return null
        entity.comment = comment?.takeIf { it.isNotBlank() }
        return enrichWithOffsetLinks(listOf(jpaRepository.save(entity))).first()
    }

    @Transactional(readOnly = true)
    override fun findByIdsAndUserId(
        ids: Set<Long>,
        userId: Long,
    ): List<Transaction> {
        if (ids.isEmpty()) return emptyList()
        return enrichWithOffsetLinks(jpaRepository.findByIdsAndUserId(ids, userId))
    }

    @Transactional
    override fun enrichByFingerprint(
        fingerprint: String,
        userId: Long,
        purpose: String?,
        counterpartyName: String?,
        counterpartyIban: String?,
        categoryGroup: String?,
    ) {
        val entity = jpaRepository.findByFingerprintAndUserId(fingerprint, userId) ?: return
        if (entity.purpose == null && !purpose.isNullOrBlank()) entity.purpose = purpose
        if (entity.counterpartyName == null && !counterpartyName.isNullOrBlank()) entity.counterpartyName = counterpartyName
        if (entity.counterpartyIban == null && !counterpartyIban.isNullOrBlank()) entity.counterpartyIban = counterpartyIban
        if (entity.categoryGroup == null && !categoryGroup.isNullOrBlank()) entity.categoryGroup = categoryGroup
        jpaRepository.save(entity)
    }

    private fun enrichWithOffsetLinks(entities: List<TransactionEntity>): List<Transaction> {
        val ids = entities.mapNotNull { it.id }
        if (ids.isEmpty()) return emptyList()
        val linksByTxId = buildLinkMap(offsetJpaRepository.findByTransactionIds(ids))
        return entities.map { it.toDomain(linksByTxId[it.id] ?: emptyList()) }
    }

    private fun buildLinkMap(offsets: List<TransactionOffsetEntity>): Map<Long, List<TransactionOffsetLink>> {
        val result = mutableMapOf<Long, MutableList<TransactionOffsetLink>>()
        for (offset in offsets) {
            val aId = requireNotNull(offset.transactionA.id)
            val bId = requireNotNull(offset.transactionB.id)
            result.getOrPut(aId) { mutableListOf() }.add(offset.toDomainLinkFor(aId))
            result.getOrPut(bId) { mutableListOf() }.add(offset.toDomainLinkFor(bId))
        }
        return result
    }

    private fun TransactionOffsetEntity.toDomainLinkFor(requestingTxId: Long): TransactionOffsetLink {
        val isOnASide = transactionA.id == requestingTxId
        val linked = if (isOnASide) transactionB else transactionA
        val requesting = if (isOnASide) transactionA else transactionB
        val myCommitted = if (isOnASide) amountA ?: requesting.amount else amountB ?: requesting.amount
        return TransactionOffsetLink(
            id = requireNotNull(id),
            linkedTransactionId = requireNotNull(linked.id),
            linkedTransactionAmount = linked.amount,
            amountA = amountA,
            amountB = amountB,
            myCommitted = myCommitted,
        )
    }

    private fun TransactionEntity.toDomain(offsetLinks: List<TransactionOffsetLink> = emptyList()) =
        Transaction(
            category = category,
            subcategory = subcategory,
            categoryGroup = categoryGroup,
            bookingDate = bookingDate,
            valueDate = valueDate,
            accountingDate = accountingDate,
            amount = amount,
            currency = currency,
            accountIban = account.iban,
            id = id,
            offsetLinks = offsetLinks,
            comment = comment,
            purpose = purpose,
            counterpartyName = counterpartyName,
            counterpartyIban = counterpartyIban,
        )

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
            category = category?.takeIf { it.isNotBlank() },
            subcategory = subcategory?.takeIf { it.isNotBlank() },
            categoryGroup = categoryGroup?.takeIf { it.isNotBlank() },
            bookingDate = bookingDate,
            valueDate = valueDate,
            accountingDate = accountingDate,
            amount = amount,
            currency = currency,
            account = account,
            fingerprint = fingerprint,
            user = userJpaRepository.getReferenceById(userId),
            purpose = purpose?.takeIf { it.isNotBlank() },
            counterpartyName = counterpartyName?.takeIf { it.isNotBlank() },
            counterpartyIban = counterpartyIban?.takeIf { it.isNotBlank() },
        )
    }

    private fun Transaction.fingerprintRaw() =
        "$accountIban|$bookingDate|$valueDate|${amount.stripTrailingZeros().toPlainString()}|$currency"

    private fun sha256(raw: String) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
