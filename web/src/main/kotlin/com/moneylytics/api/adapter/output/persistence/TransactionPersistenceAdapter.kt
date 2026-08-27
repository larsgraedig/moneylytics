package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CategoryUpdateEntry
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.BudgetTransactionSummary
import com.moneylytics.api.domain.CollectionSummary
import com.moneylytics.api.domain.Transaction
import com.moneylytics.api.domain.TransactionGroupSummary
import com.moneylytics.api.domain.TransactionOffsetLink
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate

@Suppress("TooManyFunctions")
@Component
class TransactionPersistenceAdapter(
    private val jpaRepository: TransactionJpaRepository,
    private val accountJpaRepository: AccountJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
    private val offsetJpaRepository: TransactionOffsetJpaRepository,
    private val groupMemberJpaRepository: TransactionGroupMemberJpaRepository,
    private val groupJpaRepository: TransactionGroupJpaRepository,
    private val collectionTransactionJpaRepository: CollectionTransactionJpaRepository,
    private val collectionJpaRepository: CollectionJpaRepository,
    private val budgetTransactionJpaRepository: BudgetTransactionJpaRepository,
    private val categoryJpaRepository: CategoryJpaRepository,
) : TransactionRepository {
    @Transactional
    override fun saveAll(
        transactions: List<Transaction>,
        organizationId: Long,
    ): Pair<Int, List<Long>> {
        if (transactions.isEmpty()) return 0 to emptyList()

        val categoryMap = buildCategoryMap(transactions, organizationId)
        val withFingerprints = assignFingerprints(transactions)
        val allExistingFingerprints =
            jpaRepository
                .findAllExistingFingerprints(withFingerprints.map { it.second }, organizationId)
                .toHashSet()

        val reactivatedIds =
            if (allExistingFingerprints.isNotEmpty()) {
                jpaRepository
                    .findExcludedIdsByFingerprints(allExistingFingerprints, organizationId)
                    .also { ids -> if (ids.isNotEmpty()) jpaRepository.reactivateByIds(ids) }
            } else {
                emptyList()
            }

        val newEntities =
            withFingerprints
                .filter { (_, fp) -> fp !in allExistingFingerprints }
                .map { (tx, fp) -> tx.toEntity(fp, organizationId, categoryMap) }

        val newIds = jpaRepository.saveAll(newEntities).mapNotNull { it.id }
        val allAffectedIds = newIds + reactivatedIds
        return allAffectedIds.size to allAffectedIds
    }

    @Transactional
    override fun linkToImport(
        importId: Long,
        transactionIds: List<Long>,
    ) {
        if (transactionIds.isEmpty()) return
        jpaRepository.setImportId(importId, transactionIds)
    }

    @Transactional
    override fun excludeByImportId(
        importId: Long,
        organizationId: Long,
    ) {
        jpaRepository.excludeByImportId(importId, organizationId)
    }

    @Transactional
    override fun excludeByIds(
        ids: List<Long>,
        organizationId: Long,
    ) {
        if (ids.isNotEmpty()) jpaRepository.excludeByIds(ids, organizationId)
    }

    @Transactional(readOnly = true)
    override fun findIdsByFingerprints(
        fingerprints: List<String>,
        organizationId: Long,
    ): List<Long> {
        if (fingerprints.isEmpty()) return emptyList()
        return jpaRepository.findIdsByFingerprints(fingerprints, organizationId)
    }

    @Transactional
    override fun linkToImportFile(
        importFileId: Long,
        transactionIds: List<Long>,
    ) {
        if (transactionIds.isEmpty()) return
        jpaRepository.setImportFileId(importFileId, transactionIds)
    }

    @Transactional(readOnly = true)
    override fun findIdsByImportId(importId: Long): List<Long> = jpaRepository.findIdsByImportId(importId)

    @Transactional(readOnly = true)
    override fun findByImportId(
        importId: Long,
        organizationId: Long,
    ): List<Transaction> = enrichWithOffsetLinks(jpaRepository.findByImportIdAndOrganizationId(importId, organizationId))

    @Transactional(readOnly = true)
    override fun findByImportFileId(
        fileId: Long,
        organizationId: Long,
    ): List<Transaction> = enrichWithOffsetLinks(jpaRepository.findByImportFileIdAndOrganizationId(fileId, organizationId))

    private fun buildCategoryMap(
        transactions: List<Transaction>,
        organizationId: Long,
    ): Map<Triple<String, String?, String?>, CategoryEntity> {
        val keys =
            transactions
                .filter { !it.category.isNullOrBlank() }
                .map { Triple(requireNotNull(it.category), it.subcategory, it.group) }
                .toSet()
        if (keys.isEmpty()) return emptyMap()

        val org = organizationJpaRepository.getReferenceById(organizationId)
        return keys.associateWith { (cat, sub, group) ->
            findOrCreateCategoryPath(listOfNotNull(cat, sub, group).filter { it.isNotBlank() }, organizationId, org)
        }
    }

    private fun findOrCreateCategoryPath(
        path: List<String>,
        organizationId: Long,
        org: OrganizationEntity,
    ): CategoryEntity {
        var parentId: Long? = null
        var current: CategoryEntity? = null
        for (name in path) {
            current =
                categoryJpaRepository.findByNameAndParentIdAndOrganizationId(name, parentId, organizationId)
                    ?: categoryJpaRepository.save(
                        CategoryEntity(
                            name = name,
                            parent = parentId?.let { categoryJpaRepository.getReferenceById(it) },
                            organization = org,
                        ),
                    )
            parentId = requireNotNull(current.id)
        }
        return requireNotNull(current) { "Path must not be empty" }
    }

    private fun assignFingerprints(transactions: List<Transaction>): List<Pair<Transaction, String>> {
        val counts = mutableMapOf<String, Int>()
        return transactions.map { tx ->
            val raw = tx.fingerprintRaw()
            val n = (counts.getOrDefault(raw, 0) + 1).also { counts[raw] = it }
            tx to sha256(if (n == 1) raw else "$raw:${n - 1}")
        }
    }

    @Transactional(readOnly = true)
    override fun findExistingFingerprints(
        fingerprints: Collection<String>,
        organizationId: Long,
    ): Set<String> = jpaRepository.findExistingFingerprints(fingerprints, organizationId).toHashSet()

    @Transactional(readOnly = true)
    override fun findByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        organizationId: Long,
        accountId: Long?,
    ): List<Transaction> {
        val entities =
            if (accountId != null) {
                jpaRepository.findByOrganizationIdAndAccountIdAndAccountingDateBetweenAndExcludedFalse(
                    organizationId,
                    accountId,
                    from,
                    to,
                )
            } else {
                jpaRepository.findByOrganizationIdAndAccountingDateBetweenAndExcludedFalse(organizationId, from, to)
            }
        return enrichWithOffsetLinks(entities)
    }

    @Transactional(readOnly = true)
    override fun findNegativeByAccountingDateBetween(
        from: LocalDate,
        to: LocalDate,
        organizationId: Long,
        accountId: Long?,
    ): List<Transaction> {
        val entities =
            if (accountId != null) {
                jpaRepository.findByOrganizationIdAndAccountIdAndAccountingDateBetweenAndAmountLessThanAndExcludedFalse(
                    organizationId,
                    accountId,
                    from,
                    to,
                    BigDecimal.ZERO,
                )
            } else {
                jpaRepository.findByOrganizationIdAndAccountingDateBetweenAndAmountLessThanAndExcludedFalse(
                    organizationId,
                    from,
                    to,
                    BigDecimal.ZERO,
                )
            }
        return enrichWithOffsetLinks(entities)
    }

    @Transactional
    override fun updateAccountingDate(
        id: Long,
        organizationId: Long,
        accountingDate: LocalDate,
    ): Transaction? {
        val entity = jpaRepository.findByIdAndOrganizationId(id, organizationId) ?: return null
        entity.accountingDate = accountingDate
        return enrichWithOffsetLinks(listOf(jpaRepository.save(entity))).first()
    }

    @Transactional(readOnly = true)
    override fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): Transaction? {
        val entity = jpaRepository.findByIdAndOrganizationId(id, organizationId) ?: return null
        return enrichWithOffsetLinks(listOf(entity)).first()
    }

    @Transactional
    override fun updateCategory(
        id: Long,
        organizationId: Long,
        categoryId: Long?,
    ): Transaction? {
        val entity = jpaRepository.findByIdAndOrganizationId(id, organizationId) ?: return null
        entity.category =
            if (categoryId != null) categoryJpaRepository.getReferenceById(categoryId) else null
        return enrichWithOffsetLinks(listOf(jpaRepository.save(entity))).first()
    }

    @Transactional
    override fun updateComment(
        id: Long,
        organizationId: Long,
        comment: String?,
    ): Transaction? {
        val entity = jpaRepository.findByIdAndOrganizationId(id, organizationId) ?: return null
        entity.comment = comment?.takeIf { it.isNotBlank() }
        return enrichWithOffsetLinks(listOf(jpaRepository.save(entity))).first()
    }

    @Transactional(readOnly = true)
    override fun findByIdsAndOrganizationId(
        ids: Set<Long>,
        organizationId: Long,
    ): List<Transaction> {
        if (ids.isEmpty()) return emptyList()
        return enrichWithOffsetLinks(jpaRepository.findByIdsAndOrganizationId(ids, organizationId))
    }

    @Transactional
    override fun enrichByFingerprint(
        fingerprint: String,
        organizationId: Long,
        purpose: String?,
        counterpartyName: String?,
        counterpartyIban: String?,
    ) {
        val entity = jpaRepository.findByFingerprintAndOrganizationId(fingerprint, organizationId) ?: return
        if (entity.purpose == null && !purpose.isNullOrBlank()) entity.purpose = purpose
        if (entity.counterpartyName == null && !counterpartyName.isNullOrBlank()) entity.counterpartyName = counterpartyName
        if (entity.counterpartyIban == null && !counterpartyIban.isNullOrBlank()) entity.counterpartyIban = counterpartyIban
        jpaRepository.save(entity)
    }

    @Transactional(readOnly = true)
    override fun latestTransactionDatesByOrganizationId(organizationId: Long): Map<String, LocalDate> =
        jpaRepository
            .findLatestDatePerIban(organizationId)
            .associate { row -> row[0] as String to row[1] as LocalDate }

    @Transactional(readOnly = true)
    override fun findAssignedTransactionIdsByCollectionId(collectionId: Long): Set<Long> =
        collectionTransactionJpaRepository.findTransactionIdsByCollectionId(collectionId).toHashSet()

    @Transactional
    override fun bulkUpdateCategory(
        updates: List<CategoryUpdateEntry>,
        organizationId: Long,
    ): List<Transaction> {
        if (updates.isEmpty()) return emptyList()
        val ids = updates.map { it.id }.toSet()
        val entities = jpaRepository.findByIdsAndOrganizationId(ids, organizationId).associateBy { requireNotNull(it.id) }
        val updateMap = updates.associateBy { it.id }
        val toSave =
            entities.mapNotNull { (id, entity) ->
                val u = updateMap[id] ?: return@mapNotNull null
                entity.category =
                    if (u.categoryId != null) categoryJpaRepository.getReferenceById(u.categoryId) else null
                entity
            }
        return enrichWithOffsetLinks(jpaRepository.saveAll(toSave))
    }

    private fun enrichWithOffsetLinks(entities: List<TransactionEntity>): List<Transaction> {
        val ids = entities.mapNotNull { it.id }
        if (ids.isEmpty()) return emptyList()
        val linksByTxId = buildLinkMap(offsetJpaRepository.findByTransactionIds(ids))
        val groupsByTxId = buildGroupMap(ids)
        val collectionsByTxId = buildCollectionMap(ids)
        val budgetsByTxId = buildBudgetMap(ids)
        return entities.map {
            it.toDomain(
                linksByTxId[it.id] ?: emptyList(),
                groupsByTxId[it.id] ?: emptyList(),
                collectionsByTxId[it.id] ?: emptyList(),
                budgetsByTxId[it.id] ?: emptyList(),
            )
        }
    }

    private fun buildBudgetMap(ids: List<Long>): Map<Long, List<BudgetTransactionSummary>> {
        if (ids.isEmpty()) return emptyMap()
        val links = budgetTransactionJpaRepository.findByTransactionIds(ids)
        if (links.isEmpty()) return emptyMap()
        return links
            .groupBy { requireNotNull(it.transaction.id) }
            .mapValues { (_, linkList) ->
                linkList.map { link ->
                    BudgetTransactionSummary(
                        linkId = requireNotNull(link.id),
                        budgetId = requireNotNull(link.budget.id),
                        budgetName = link.budget.name,
                        amount = link.amount,
                    )
                }
            }
    }

    private fun buildCollectionMap(ids: List<Long>): Map<Long, List<CollectionSummary>> {
        if (ids.isEmpty()) return emptyMap()
        val members = collectionTransactionJpaRepository.findByTransactionIds(ids)
        if (members.isEmpty()) return emptyMap()
        val collectionsById =
            collectionJpaRepository
                .findAllById(members.map { it.collectionId }.toSet())
                .associateBy { requireNotNull(it.id) }
        return members
            .groupBy { it.transactionId }
            .mapValues { (_, mems) ->
                mems.mapNotNull { m -> collectionsById[m.collectionId]?.let { CollectionSummary(requireNotNull(it.id), it.name) } }
            }
    }

    private fun buildGroupMap(ids: List<Long>): Map<Long, List<TransactionGroupSummary>> {
        if (ids.isEmpty()) return emptyMap()
        val members = groupMemberJpaRepository.findByTransactionIds(ids)
        if (members.isEmpty()) return emptyMap()
        val groupsById =
            groupJpaRepository
                .findAllById(members.map { it.groupId }.toSet())
                .associateBy { requireNotNull(it.id) }
        return members
            .groupBy { it.transactionId }
            .mapValues { (_, mems) ->
                mems.mapNotNull { m -> groupsById[m.groupId]?.let { TransactionGroupSummary(requireNotNull(it.id), it.name) } }
            }
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
            groupId = groupId,
            comment = comment,
        )
    }

    private fun CategoryEntity.pathFromRoot(): List<String> {
        val path = ArrayDeque<String>()
        var node: CategoryEntity? = this
        while (node != null) {
            path.addFirst(node.name)
            node = node.parent
        }
        return path.toList()
    }

    private fun TransactionEntity.toDomain(
        offsetLinks: List<TransactionOffsetLink> = emptyList(),
        groups: List<TransactionGroupSummary> = emptyList(),
        collections: List<CollectionSummary> = emptyList(),
        budgetLinks: List<BudgetTransactionSummary> = emptyList(),
        children: List<Transaction> = emptyList(),
    ): Transaction {
        val categoryPath = category?.pathFromRoot() ?: emptyList()
        return toDomainWith(categoryPath, offsetLinks, groups, collections, budgetLinks, children)
    }

    private fun TransactionEntity.toDomainWith(
        categoryPath: List<String>,
        offsetLinks: List<TransactionOffsetLink> = emptyList(),
        groups: List<TransactionGroupSummary> = emptyList(),
        collections: List<CollectionSummary> = emptyList(),
        budgetLinks: List<BudgetTransactionSummary> = emptyList(),
        children: List<Transaction> = emptyList(),
    ) = Transaction(
        categoryId = category?.id,
        category = categoryPath.getOrNull(0),
        subcategory = categoryPath.getOrNull(1),
        group = categoryPath.getOrNull(2),
        bookingDate = bookingDate,
        valueDate = valueDate,
        accountingDate = accountingDate,
        amount = amount,
        currency = currency,
        accountIban = account.iban,
        id = id,
        offsetLinks = offsetLinks,
        groups = groups,
        collections = collections,
        comment = comment,
        purpose = purpose,
        counterpartyName = counterpartyName,
        counterpartyIban = counterpartyIban,
        budgetLinks = budgetLinks,
        parentId = parentId,
        isVirtual = isVirtual,
        excluded = excluded,
        children = children,
    )

    private fun Transaction.toEntity(
        fingerprint: String?,
        organizationId: Long,
        categoryMap: Map<Triple<String, String?, String?>, CategoryEntity>,
    ): TransactionEntity {
        val account =
            accountJpaRepository.findByIbanAndOrganizationId(accountIban, organizationId)
                ?: error(
                    "Account not found for IBAN $accountIban and organizationId $organizationId — ensure accounts are created before importing transactions",
                )
        val categoryEntity = if (!category.isNullOrBlank()) categoryMap[Triple(category, subcategory, group)] else null
        return TransactionEntity(
            category = categoryEntity,
            bookingDate = bookingDate,
            valueDate = valueDate,
            accountingDate = accountingDate,
            amount = amount,
            currency = currency,
            account = account,
            fingerprint = fingerprint,
            organization = organizationJpaRepository.getReferenceById(organizationId),
            purpose = purpose?.takeIf { it.isNotBlank() },
            counterpartyName = counterpartyName?.takeIf { it.isNotBlank() },
            counterpartyIban = counterpartyIban?.takeIf { it.isNotBlank() },
            parentId = parentId,
            isVirtual = isVirtual,
            excluded = excluded,
        )
    }

    override fun countByCategoryGrouped(
        organizationId: Long,
        accountId: Long?,
    ): Map<Long, Long> =
        jpaRepository
            .countByCategoryGrouped(organizationId, accountId)
            .associate { row -> (row[0] as Long) to (row[1] as Long) }

    override fun countByCategoryGroupedInPeriod(
        organizationId: Long,
        from: java.time.LocalDate,
        to: java.time.LocalDate,
        accountId: Long?,
    ): Map<Long, Long> =
        jpaRepository
            .countByCategoryGroupedInPeriod(organizationId, from, to, accountId)
            .associate { row -> (row[0] as Long) to (row[1] as Long) }

    @Transactional(readOnly = true)
    override fun findIdsByCategoryId(
        categoryId: Long,
        organizationId: Long,
    ): List<Long> = jpaRepository.findIdsByCategoryId(categoryId, organizationId)

    @Transactional
    override fun moveToCategoryBySource(
        sourceCategoryId: Long,
        targetCategoryId: Long,
        organizationId: Long,
    ) {
        jpaRepository.moveByCategoryId(sourceCategoryId, targetCategoryId, organizationId)
    }

    @Transactional
    override fun moveToCategoryBulk(
        transactionIds: List<Long>,
        targetCategoryId: Long,
        organizationId: Long,
    ) {
        if (transactionIds.isEmpty()) return
        jpaRepository.moveBulkToCategory(transactionIds, targetCategoryId, organizationId)
    }

    private fun Transaction.fingerprintRaw() =
        "$accountIban|$bookingDate|$valueDate|${amount.stripTrailingZeros().toPlainString()}|$currency"

    private fun sha256(raw: String) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
