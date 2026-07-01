package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetLinkedTransactionsUseCase
import com.moneylytics.api.application.port.input.LinkedTransactionGroup
import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.input.UpdateOffsetGroupMetaUseCase
import com.moneylytics.api.application.port.output.CreateOffsetLinkCommand
import com.moneylytics.api.application.port.output.OffsetGroupMetaRepository
import com.moneylytics.api.application.port.output.OffsetLinkResult
import com.moneylytics.api.application.port.output.TransactionOffsetRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import org.springframework.stereotype.Service

@Service
class TransactionOffsetService(
    private val offsetRepository: TransactionOffsetRepository,
    private val transactionRepository: TransactionRepository,
    private val groupMetaRepository: OffsetGroupMetaRepository,
) : ManageTransactionOffsetUseCase,
    GetLinkedTransactionsUseCase,
    UpdateOffsetGroupMetaUseCase {
    override fun linkTransactions(command: LinkTransactionsCommand): OffsetLinkResult {
        require(command.transactionId != command.otherTransactionId) {
            "A transaction cannot be linked to itself"
        }
        requireNotNull(transactionRepository.findByIdAndUserId(command.transactionId, command.userId)) {
            "Transaction ${command.transactionId} not found"
        }
        requireNotNull(transactionRepository.findByIdAndUserId(command.otherTransactionId, command.userId)) {
            "Transaction ${command.otherTransactionId} not found"
        }

        val (aId, bId) =
            if (command.transactionId < command.otherTransactionId) {
                command.transactionId to command.otherTransactionId
            } else {
                command.otherTransactionId to command.transactionId
            }

        check(!offsetRepository.existsByPair(aId, bId)) {
            "Transactions ${command.transactionId} and ${command.otherTransactionId} are already linked"
        }

        return offsetRepository.create(CreateOffsetLinkCommand(aId, bId, command.partialAmount))
    }

    override fun unlinkTransactions(
        linkId: Long,
        userId: Long,
    ): Boolean {
        val pairsBefore = offsetRepository.findIdPairsForUser(userId)
        val groupsBefore = groupsByKey(pairsBefore)

        val deleted = offsetRepository.delete(linkId, userId)
        if (!deleted) return false

        val pairsAfter = offsetRepository.findIdPairsForUser(userId)
        val groupsAfter = groupsByKey(pairsAfter)
        migrateGroupMeta(userId, groupsBefore, groupsAfter)

        return true
    }

    override fun getLinkedGroups(userId: Long): List<LinkedTransactionGroup> {
        val pairs = offsetRepository.findIdPairsForUser(userId)
        if (pairs.isEmpty()) return emptyList()

        val groups = groupsByKey(pairs)
        val allIds = groups.values.flatten().toSet()
        val byId = transactionRepository.findByIdsAndUserId(allIds, userId).associateBy { it.id!! }
        val metaByKey = groupMetaRepository.findAllForUser(userId)

        return groups
            .map { (groupKey, ids) ->
                val (name, comment) = metaByKey[groupKey] ?: (null to null)
                LinkedTransactionGroup(
                    groupKey = groupKey,
                    name = name,
                    comment = comment,
                    transactions = ids.mapNotNull { byId[it] }.sortedBy { it.accountingDate },
                )
            }
            .sortedByDescending { it.transactions.first().accountingDate }
    }

    override fun updateGroupMeta(
        userId: Long,
        groupKey: Long,
        name: String?,
        comment: String?,
    ) = groupMetaRepository.upsert(userId, groupKey, name, comment)

    // Returns a map of groupKey (min transaction ID) → set of transaction IDs in that group.
    private fun groupsByKey(pairs: List<Pair<Long, Long>>): Map<Long, Set<Long>> {
        if (pairs.isEmpty()) return emptyMap()
        val parent = mutableMapOf<Long, Long>()
        fun find(x: Long): Long {
            if (parent[x] == null) parent[x] = x
            if (parent[x] != x) parent[x] = find(parent[x]!!)
            return parent[x]!!
        }
        fun union(a: Long, b: Long) { parent[find(a)] = find(b) }
        val allIds = mutableSetOf<Long>()
        for ((a, b) in pairs) { allIds += a; allIds += b; union(a, b) }
        return allIds.groupBy { find(it) }.values.associate { ids -> ids.min() to ids.toSet() }
    }

    // After a link is removed a group may split. For every old group key that had metadata
    // and whose key transaction is no longer part of any linked group, find the new group(s)
    // that contain the old members and migrate the metadata to their new key.
    private fun migrateGroupMeta(
        userId: Long,
        before: Map<Long, Set<Long>>,
        after: Map<Long, Set<Long>>,
    ) {
        val metaByKey = groupMetaRepository.findAllForUser(userId)
        // Only process groups that had metadata and whose key is not in the new groups.
        for ((oldKey, oldMembers) in before) {
            val (name, comment) = metaByKey[oldKey] ?: continue
            if (name == null && comment == null) continue
            if (after.containsKey(oldKey)) continue // key survived unchanged

            // Find which new group(s) contain members of this old group.
            val successorKeys = after
                .filter { (_, newMembers) -> newMembers.any { it in oldMembers } }
                .keys
                .sorted()

            // Migrate to the first (lowest) successor that doesn't already have metadata.
            for (newKey in successorKeys) {
                val existingMeta = metaByKey[newKey]
                if (existingMeta == null || (existingMeta.first == null && existingMeta.second == null)) {
                    groupMetaRepository.upsert(userId, newKey, name, comment)
                    break
                }
            }
        }
    }
}
