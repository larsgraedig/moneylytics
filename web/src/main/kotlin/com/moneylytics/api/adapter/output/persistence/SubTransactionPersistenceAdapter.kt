package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.SubTransactionPort
import com.moneylytics.api.domain.SplitItem
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Component
class SubTransactionPersistenceAdapter(
    private val jpaRepository: TransactionJpaRepository,
    private val accountJpaRepository: AccountJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
    private val categoryJpaRepository: CategoryJpaRepository,
) : SubTransactionPort {
    @Transactional
    override fun createVirtualChildren(
        parent: Transaction,
        splits: List<SplitItem>,
        organizationId: Long,
    ): List<Transaction> {
        val parentId = requireNotNull(parent.id)
        val account =
            accountJpaRepository.findByIbanAndOrganizationId(parent.accountIban, organizationId)
                ?: error("Account not found for IBAN ${parent.accountIban}")
        val org = organizationJpaRepository.getReferenceById(organizationId)
        val entities =
            splits.map { split ->
                TransactionEntity(
                    category =
                        if (split.categoryId != null) categoryJpaRepository.getReferenceById(split.categoryId) else null,
                    bookingDate = parent.bookingDate,
                    valueDate = parent.valueDate,
                    accountingDate = parent.accountingDate,
                    amount = split.amount,
                    currency = parent.currency,
                    account = account,
                    fingerprint = null,
                    organization = org,
                    comment = split.comment?.takeIf { it.isNotBlank() },
                    parentId = parentId,
                    isVirtual = true,
                )
            }
        return jpaRepository.saveAll(entities).map { it.toSimpleDomain() }
    }

    @Transactional
    override fun createVirtualParent(
        children: List<Transaction>,
        accountingDate: LocalDate,
        name: String?,
        comment: String?,
        organizationId: Long,
    ): Transaction {
        val first = children.first()
        val account =
            accountJpaRepository.findByIbanAndOrganizationId(first.accountIban, organizationId)
                ?: error("Account not found for IBAN ${first.accountIban}")
        val org = organizationJpaRepository.getReferenceById(organizationId)
        val totalAmount = children.sumOf { it.amount }
        val entity =
            TransactionEntity(
                bookingDate = accountingDate,
                valueDate = accountingDate,
                accountingDate = accountingDate,
                amount = totalAmount,
                currency = first.currency,
                account = account,
                fingerprint = null,
                organization = org,
                comment = comment?.takeIf { it.isNotBlank() },
                purpose = name?.takeIf { it.isNotBlank() },
                isVirtual = true,
            )
        return jpaRepository.save(entity).toSimpleDomain()
    }

    @Transactional
    override fun setExcluded(
        transactionId: Long,
        organizationId: Long,
        excluded: Boolean,
    ) {
        val entity = jpaRepository.findByIdAndOrganizationId(transactionId, organizationId) ?: return
        entity.excluded = excluded
        jpaRepository.save(entity)
    }

    @Transactional
    override fun setParentId(
        transactionId: Long,
        organizationId: Long,
        parentId: Long?,
    ) {
        val entity = jpaRepository.findByIdAndOrganizationId(transactionId, organizationId) ?: return
        entity.parentId = parentId
        jpaRepository.save(entity)
    }

    @Transactional(readOnly = true)
    override fun findChildren(
        parentId: Long,
        organizationId: Long,
    ): List<Transaction> =
        jpaRepository
            .findByParentIdAndOrganizationId(parentId, organizationId)
            .map { it.toSimpleDomain() }

    @Transactional
    override fun deleteVirtual(
        transactionId: Long,
        organizationId: Long,
    ) {
        val entity = jpaRepository.findByIdAndOrganizationId(transactionId, organizationId) ?: return
        require(entity.isVirtual) { "Only virtual transactions can be deleted via this operation" }
        jpaRepository.delete(entity)
    }

    @Transactional
    override fun updateAmount(
        transactionId: Long,
        organizationId: Long,
        amount: BigDecimal,
    ) {
        val entity = jpaRepository.findByIdAndOrganizationId(transactionId, organizationId) ?: return
        entity.amount = amount
        jpaRepository.save(entity)
    }

    @Transactional
    override fun createStandaloneVirtual(
        amount: BigDecimal,
        currency: String,
        accountIban: String,
        accountingDate: LocalDate,
        categoryId: Long?,
        counterpartyName: String?,
        purpose: String?,
        organizationId: Long,
    ): Transaction {
        val account =
            accountJpaRepository.findByIbanAndOrganizationId(accountIban, organizationId)
                ?: error("Account not found for IBAN $accountIban")
        val org = organizationJpaRepository.getReferenceById(organizationId)
        val entity =
            TransactionEntity(
                category = if (categoryId != null) categoryJpaRepository.getReferenceById(categoryId) else null,
                bookingDate = accountingDate,
                valueDate = accountingDate,
                accountingDate = accountingDate,
                amount = amount,
                currency = currency,
                account = account,
                fingerprint = null,
                organization = org,
                counterpartyName = counterpartyName?.takeIf { it.isNotBlank() },
                purpose = purpose?.takeIf { it.isNotBlank() },
                isVirtual = true,
            )
        return jpaRepository.save(entity).toSimpleDomain()
    }

    private fun TransactionEntity.toSimpleDomain() =
        Transaction(
            categoryId = category?.id,
            category = category?.name,
            subcategory = category?.subcategory,
            group = category?.groupName,
            bookingDate = bookingDate,
            valueDate = valueDate,
            accountingDate = accountingDate,
            amount = amount,
            currency = currency,
            accountIban = account.iban,
            id = id,
            comment = comment,
            purpose = purpose,
            counterpartyName = counterpartyName,
            counterpartyIban = counterpartyIban,
            parentId = parentId,
            isVirtual = isVirtual,
            excluded = excluded,
        )
}
