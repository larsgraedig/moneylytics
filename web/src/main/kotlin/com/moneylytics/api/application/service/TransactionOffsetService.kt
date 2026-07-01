package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetLinkedTransactionsUseCase
import com.moneylytics.api.application.port.input.LinkedTransactionGroup
import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.output.CreateOffsetLinkCommand
import com.moneylytics.api.application.port.output.OffsetLinkResult
import com.moneylytics.api.application.port.output.TransactionOffsetRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import org.springframework.stereotype.Service

@Service
class TransactionOffsetService(
    private val offsetRepository: TransactionOffsetRepository,
    private val transactionRepository: TransactionRepository,
) : ManageTransactionOffsetUseCase,
    GetLinkedTransactionsUseCase {
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
    ): Boolean = offsetRepository.delete(linkId, userId)

    override fun getLinkedGroups(userId: Long): List<LinkedTransactionGroup> {
        val pairs = offsetRepository.findIdPairsForUser(userId)
        if (pairs.isEmpty()) return emptyList()

        val parent = mutableMapOf<Long, Long>()
        fun find(x: Long): Long {
            if (parent[x] == null) parent[x] = x
            if (parent[x] != x) parent[x] = find(parent[x]!!)
            return parent[x]!!
        }
        fun union(a: Long, b: Long) { parent[find(a)] = find(b) }

        val allIds = mutableSetOf<Long>()
        for ((a, b) in pairs) {
            allIds += a; allIds += b
            union(a, b)
        }

        val byId = transactionRepository.findByIdsAndUserId(allIds, userId).associateBy { it.id!! }
        return allIds
            .groupBy { find(it) }
            .values
            .map { ids ->
                LinkedTransactionGroup(
                    ids.mapNotNull { byId[it] }.sortedBy { it.accountingDate },
                )
            }
            .sortedByDescending { it.transactions.first().accountingDate }
    }
}
