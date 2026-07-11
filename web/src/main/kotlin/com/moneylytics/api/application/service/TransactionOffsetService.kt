package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.AllocationExceededException
import com.moneylytics.api.application.port.input.ExistingLinkSummary
import com.moneylytics.api.application.port.input.GetLinkedTransactionsUseCase
import com.moneylytics.api.application.port.input.LinkTransactionResult
import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.input.LinkedTransactionGroup
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.output.CreateOffsetLinkCommand
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
    override fun linkTransactions(command: LinkTransactionsCommand): LinkTransactionResult {
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
            if (sourceIsA) {
                command.transactionId to command.otherTransactionId
            } else {
                command.otherTransactionId to
                    command.transactionId
            }
        val (txA, txB) = if (sourceIsA) txSource to txOther else txOther to txSource
        val rawAmountA = if (sourceIsA) command.myAmount else command.otherAmount
        val rawAmountB = if (sourceIsA) command.otherAmount else command.myAmount

        check(!offsetRepository.existsByPair(aId, bId)) {
            "Transactions ${command.transactionId} and ${command.otherTransactionId} are already linked"
        }

        val noOffset = command.myAmount == null && command.otherAmount == null && sameSign(txA.amount, txB.amount)
        val amountA = if (noOffset) null else rawAmountA?.let { normalizeSign(it, txA.amount) } ?: txA.amount
        val amountB = if (noOffset) null else rawAmountB?.let { normalizeSign(it, txB.amount) } ?: txB.amount

        if (!noOffset) {
            validateAllocation(txA, amountA!!, txB.amount)
            validateAllocation(txB, amountB!!, txA.amount)
        }

        val groupId =
            resolveGroupForLink(
                txAId = aId,
                txBId = bId,
                userId = command.userId,
                targetGroupId = command.targetGroupId,
                forceNewGroup = command.forceNewGroup,
            )

        if (!noOffset) {
            offsetRepository.create(CreateOffsetLinkCommand(aId, bId, amountA, amountB, groupId))
        }

        val updatedSource = requireNotNull(transactionRepository.findByIdAndUserId(command.transactionId, command.userId))
        val updatedOther = requireNotNull(transactionRepository.findByIdAndUserId(command.otherTransactionId, command.userId))
        return LinkTransactionResult(groupId = groupId, sourceTransaction = updatedSource, otherTransaction = updatedOther)
    }

    override fun unlinkTransactions(
        linkId: Long,
        userId: Long,
    ): Boolean {
        val deleted = offsetRepository.delete(linkId, userId) ?: return false
        deleted.groupId?.let { cleanupGroup(it, userId, fromUnlink = true) }
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
                    memberIds
                        .mapNotNull { txById[it] }
                        .map { tx ->
                            tx.copy(offsetLinks = tx.offsetLinks.filter { it.groupId == group.id })
                        }.sortedBy { it.accountingDate }
                if (transactions.isEmpty()) return@mapNotNull null
                LinkedTransactionGroup(
                    groupId = group.id,
                    name = group.name,
                    comment = group.comment,
                    transactions = transactions,
                )
            }.sortedByDescending { it.transactions.first().accountingDate }
    }

    override fun getLinkedGroup(
        groupId: Long,
        userId: Long,
    ): LinkedTransactionGroup? {
        val group = groupRepository.findById(groupId, userId) ?: return null
        val memberIds = groupRepository.findMemberIds(groupId)
        if (memberIds.isEmpty()) return null
        val txById = transactionRepository.findByIdsAndUserId(memberIds.toSet(), userId).associateBy { it.id!! }
        val transactions =
            memberIds
                .mapNotNull { txById[it] }
                .map { tx -> tx.copy(offsetLinks = tx.offsetLinks.filter { it.groupId == groupId }) }
                .sortedBy { it.accountingDate }
        if (transactions.isEmpty()) return null
        return LinkedTransactionGroup(
            groupId = group.id,
            name = group.name,
            comment = group.comment,
            transactions = transactions,
        )
    }

    override fun updateGroupMeta(
        groupId: Long,
        userId: Long,
        name: String?,
        comment: String?,
    ) {
        groupRepository.update(groupId, userId, name, comment)
    }

    override fun updateOffsetComment(
        linkId: Long,
        userId: Long,
        comment: String?,
    ) {
        offsetRepository.updateComment(linkId, userId, comment)
    }

    override fun removeTransactionFromGroup(
        txId: Long,
        groupId: Long,
        userId: Long,
    ) {
        offsetRepository.deleteByTxAndGroupId(txId, groupId, userId)
        groupRepository.removeMember(groupId, txId)
        cleanupGroup(groupId, userId)
    }

    private fun normalizeSign(
        amount: BigDecimal,
        txAmount: BigDecimal,
    ): BigDecimal = if (txAmount < BigDecimal.ZERO) -amount.abs() else amount.abs()

    private fun sameSign(
        a: BigDecimal,
        b: BigDecimal,
    ) = (a >= BigDecimal.ZERO) == (b >= BigDecimal.ZERO)

    private fun validateAllocation(
        tx: Transaction,
        myAmount: BigDecimal,
        otherTxAmount: BigDecimal,
    ) {
        // Same-sign links (e.g. income↔income) don't create real offsets — skip validation
        if (sameSign(tx.amount, otherTxAmount)) return
        val txAbs = tx.amount.abs()
        val alreadyCommitted =
            tx.offsetLinks.sumOf { link ->
                if (sameSign(tx.amount, link.linkedTransactionAmount)) return@sumOf BigDecimal.ZERO
                val a = link.amountA
                val b = link.amountB
                when {
                    a == null && b == null -> BigDecimal.ZERO
                    a != null && b != null -> minOf(a.abs(), b.abs())
                    else -> minOf(link.myCommitted.abs(), link.linkedTransactionAmount.abs())
                }
            }
        val newCommit = minOf(myAmount.abs(), otherTxAmount.abs())
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
        fromUnlink: Boolean = false,
    ) {
        val memberIds = groupRepository.findMemberIds(groupId)
        if (memberIds.isEmpty()) {
            groupRepository.delete(groupId)
            return
        }

        val remainingLinks = offsetRepository.findLinksForGroup(groupId)
        if (remainingLinks.isEmpty()) {
            if (!fromUnlink && memberIds.size >= 2) return
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
