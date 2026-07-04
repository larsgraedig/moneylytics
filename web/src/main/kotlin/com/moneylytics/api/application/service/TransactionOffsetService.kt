package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.AllocationExceededException
import com.moneylytics.api.application.port.input.ExistingLinkSummary
import com.moneylytics.api.application.port.input.GetLinkedTransactionsUseCase
import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.input.LinkedTransactionGroup
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.output.CreateOffsetLinkCommand
import com.moneylytics.api.application.port.output.OffsetLinkResult
import com.moneylytics.api.application.port.output.TransactionGroupRepository
import com.moneylytics.api.application.port.output.TransactionOffsetRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class TransactionOffsetService(
    private val offsetRepository: TransactionOffsetRepository,
    private val transactionRepository: TransactionRepository,
    private val groupRepository: TransactionGroupRepository,
) : ManageTransactionOffsetUseCase,
    GetLinkedTransactionsUseCase {
    override fun linkTransactions(command: LinkTransactionsCommand): OffsetLinkResult {
        require(command.transactionId != command.otherTransactionId) {
            "A transaction cannot be linked to itself"
        }
        val txSource =
            requireNotNull(transactionRepository.findByIdAndUserId(command.transactionId, command.userId)) {
                "Transaction ${command.transactionId} not found"
            }
        val txOther =
            requireNotNull(transactionRepository.findByIdAndUserId(command.otherTransactionId, command.userId)) {
                "Transaction ${command.otherTransactionId} not found"
            }

        val sourceIsA = command.transactionId < command.otherTransactionId
        val (aId, bId) =
            if (sourceIsA) command.transactionId to command.otherTransactionId
            else command.otherTransactionId to command.transactionId
        val (txA, txB) = if (sourceIsA) txSource to txOther else txOther to txSource
        val rawAmountA = if (sourceIsA) command.myAmount else command.otherAmount
        val rawAmountB = if (sourceIsA) command.otherAmount else command.myAmount

        check(!offsetRepository.existsByPair(aId, bId)) {
            "Transactions ${command.transactionId} and ${command.otherTransactionId} are already linked"
        }

        val amountA = rawAmountA?.let { normalizeSign(it, txA.amount) }
        val amountB = rawAmountB?.let { normalizeSign(it, txB.amount) }

        // TODO: re-enable after testing
        // validateAllocation(txA, amountA)
        // validateAllocation(txB, amountB)

        val groupId = resolveGroupForLink(
            txAId = aId,
            txBId = bId,
            userId = command.userId,
            targetGroupId = command.targetGroupId,
            forceNewGroup = command.forceNewGroup,
        )
        if (amountA == null && amountB == null) {
            return OffsetLinkResult(
                id = null,
                transactionAId = aId,
                transactionBId = bId,
                amountA = null,
                amountB = null,
                groupId = groupId,
            )
        }
        return offsetRepository.create(CreateOffsetLinkCommand(aId, bId, amountA, amountB, groupId))
    }

    override fun unlinkTransactions(
        linkId: Long,
        userId: Long,
    ): Boolean {
        val deleted = offsetRepository.delete(linkId, userId) ?: return false
        deleted.groupId?.let { cleanupGroup(it, userId) }
        return true
    }

    override fun getLinkedGroups(userId: Long): List<LinkedTransactionGroup> {
        val groups = groupRepository.findAllByUserId(userId)
        if (groups.isEmpty()) return emptyList()

        val groupMemberIds = groups.associate { it.id to groupRepository.findMemberIds(it.id) }
        val allTxIds = groupMemberIds.values.flatten().toSet()
        if (allTxIds.isEmpty()) return emptyList()

        val txById = transactionRepository.findByIdsAndUserId(allTxIds, userId).associateBy { it.id!! }

        return groups
            .mapNotNull { group ->
                val memberIds = groupMemberIds[group.id] ?: return@mapNotNull null
                val transactions =
                    memberIds.mapNotNull { txById[it] }
                        .map { tx ->
                            tx.copy(offsetLinks = tx.offsetLinks.filter { it.groupId == group.id })
                        }
                        .sortedBy { it.accountingDate }
                if (transactions.isEmpty()) return@mapNotNull null
                LinkedTransactionGroup(
                    groupId = group.id,
                    name = group.name,
                    comment = group.comment,
                    transactions = transactions,
                )
            }.sortedByDescending { it.transactions.first().accountingDate }
    }

    override fun updateGroupMeta(
        groupId: Long,
        userId: Long,
        name: String?,
        comment: String?,
    ) {
        groupRepository.update(groupId, userId, name, comment)
    }

    private fun normalizeSign(
        amount: BigDecimal,
        txAmount: BigDecimal,
    ): BigDecimal = if (txAmount < BigDecimal.ZERO) -amount.abs() else amount.abs()

    private fun validateAllocation(
        tx: Transaction,
        myAmount: BigDecimal?,
    ) {
        val txAbs = tx.amount.abs()
        val alreadyCommitted = tx.offsetLinks.sumOf { it.myCommitted.abs() }
        val newCommit = myAmount?.abs() ?: txAbs
        val total = alreadyCommitted + newCommit
        if (total > txAbs) {
            val maxRemaining = (txAbs - alreadyCommitted).max(BigDecimal.ZERO)
            throw AllocationExceededException(
                transactionId = requireNotNull(tx.id),
                maxRemaining = maxRemaining,
                existingLinks =
                    tx.offsetLinks.map { link ->
                        ExistingLinkSummary(
                            linkId = link.id,
                            linkedTransactionId = link.linkedTransactionId,
                            committedAmount = link.myCommitted.abs(),
                        )
                    },
            )
        }
    }

    private fun resolveGroupForLink(
        txAId: Long,
        txBId: Long,
        userId: Long,
        targetGroupId: Long?,
        forceNewGroup: Boolean,
    ): Long {
        if (targetGroupId != null) {
            groupRepository.addMember(targetGroupId, txAId)
            groupRepository.addMember(targetGroupId, txBId)
            return targetGroupId
        }
        if (forceNewGroup) {
            val newGroup = groupRepository.create(userId)
            groupRepository.addMember(newGroup.id, txAId)
            groupRepository.addMember(newGroup.id, txBId)
            return newGroup.id
        }
        val groupA = groupRepository.findGroupIdsForTransaction(txAId).firstOrNull()
        val groupB = groupRepository.findGroupIdsForTransaction(txBId).firstOrNull()
        return when {
            groupA != null -> {
                groupRepository.addMember(groupA, txBId)
                groupA
            }
            groupB != null -> {
                groupRepository.addMember(groupB, txAId)
                groupB
            }
            else -> {
                val newGroup = groupRepository.create(userId)
                groupRepository.addMember(newGroup.id, txAId)
                groupRepository.addMember(newGroup.id, txBId)
                newGroup.id
            }
        }
    }

    private fun cleanupGroup(
        groupId: Long,
        userId: Long,
    ) {
        val memberIds = groupRepository.findMemberIds(groupId)
        if (memberIds.isEmpty()) {
            groupRepository.delete(groupId)
            return
        }

        val remainingLinks = offsetRepository.findLinksForGroup(groupId)
        if (remainingLinks.isEmpty()) {
            memberIds.forEach { groupRepository.removeMember(groupId, it) }
            groupRepository.delete(groupId)
            return
        }

        val components = connectedComponents(memberIds.toSet(), remainingLinks)
        if (components.size == 1) {
            if (components.first().size < 2) {
                components.first().forEach { groupRepository.removeMember(groupId, it) }
                groupRepository.delete(groupId)
            }
            return
        }

        val sorted = components.sortedByDescending { it.size }
        val mainComponent = sorted.first()

        if (mainComponent.size < 2) {
            memberIds.forEach { groupRepository.removeMember(groupId, it) }
            groupRepository.delete(groupId)
            return
        }

        val toRemove = memberIds.toSet() - mainComponent
        toRemove.forEach { groupRepository.removeMember(groupId, it) }

        for (component in sorted.drop(1)) {
            if (component.size >= 2) {
                val newGroup = groupRepository.create(userId)
                component.forEach { groupRepository.addMember(newGroup.id, it) }
                offsetRepository.updateLinksGroupId(groupId, newGroup.id, component)
            }
        }
    }

    private fun connectedComponents(
        nodeIds: Set<Long>,
        edges: List<Pair<Long, Long>>,
    ): List<Set<Long>> {
        val parent = nodeIds.associateWith { it }.toMutableMap()

        fun find(x: Long): Long {
            if (parent[x] != x) parent[x] = find(parent[x]!!)
            return parent[x]!!
        }

        fun union(
            a: Long,
            b: Long,
        ) {
            parent[find(a)] = find(b)
        }
        for ((a, b) in edges) {
            if (a in parent && b in parent) union(a, b)
        }
        return nodeIds.groupBy { find(it) }.values.map { it.toSet() }
    }
}
