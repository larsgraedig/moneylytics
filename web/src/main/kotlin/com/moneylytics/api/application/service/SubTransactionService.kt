package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetSubTransactionGroupUseCase
import com.moneylytics.api.application.port.input.ManageSubTransactionUseCase
import com.moneylytics.api.application.port.input.MergeTransactionsCommand
import com.moneylytics.api.application.port.input.SplitTransactionCommand
import com.moneylytics.api.application.port.output.SubTransactionPort
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.SubTransactionGroup
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubTransactionService(
    private val transactionRepository: TransactionRepository,
    private val subTransactionPort: SubTransactionPort,
) : ManageSubTransactionUseCase,
    GetSubTransactionGroupUseCase {
    @Transactional
    override fun splitTransaction(command: SplitTransactionCommand): SubTransactionGroup {
        val parent = requireTransaction(command.transactionId, command.organizationId)

        require(!parent.isVirtual) { "Virtual transactions cannot be split" }
        require(parent.parentId == null) { "A child transaction cannot be split" }
        require(!parent.excluded) { "This transaction is already part of a sub-transaction group" }
        require(command.splits.size >= 2) { "At least 2 splits are required" }
        require(parent.offsetLinks.isEmpty()) {
            "Transactions with offset links (Verrechnungen) cannot be split"
        }

        val parentAmount = parent.amount
        val splitSum = command.splits.sumOf { it.amount }
        require(splitSum.compareTo(parentAmount) == 0) {
            "Sum of split amounts ($splitSum) must equal parent amount ($parentAmount)"
        }

        val children = subTransactionPort.createVirtualChildren(parent, command.splits, command.organizationId)
        subTransactionPort.setExcluded(command.transactionId, command.organizationId, true)

        val updatedParent = requireTransaction(command.transactionId, command.organizationId)
        return SubTransactionGroup(parent = updatedParent, children = children)
    }

    @Transactional
    override fun unsplitTransaction(
        transactionId: Long,
        organizationId: Long,
    ) {
        val parent = requireTransaction(transactionId, organizationId)
        require(!parent.isVirtual) { "Transaction is not a physical parent" }

        val children = subTransactionPort.findChildren(transactionId, organizationId)
        require(children.isNotEmpty()) { "Transaction has no children to unsplit" }

        for (child in children) {
            val childId = requireNotNull(child.id)
            val loadedChild = requireTransaction(childId, organizationId)
            require(loadedChild.offsetLinks.isEmpty()) {
                "Cannot unsplit: child transaction $childId has offset links (Verrechnungen)"
            }
            subTransactionPort.deleteVirtual(childId, organizationId)
        }

        subTransactionPort.setExcluded(transactionId, organizationId, false)
    }

    @Transactional
    override fun mergeTransactions(command: MergeTransactionsCommand): SubTransactionGroup {
        require(command.transactionIds.size >= 2) { "At least 2 transactions are required for a merge" }
        require(command.transactionIds.toSet().size == command.transactionIds.size) { "Duplicate transaction IDs" }

        val transactions = command.transactionIds.map { id -> requireTransaction(id, command.organizationId) }
        validateMergeCandidates(transactions)

        val virtualParent =
            subTransactionPort.createVirtualParent(
                children = transactions,
                accountingDate = command.accountingDate,
                name = command.name,
                comment = command.comment,
                organizationId = command.organizationId,
            )
        val parentId = requireNotNull(virtualParent.id)

        for (tx in transactions) {
            subTransactionPort.setParentId(requireNotNull(tx.id), command.organizationId, parentId)
            subTransactionPort.setExcluded(requireNotNull(tx.id), command.organizationId, true)
        }

        val updatedChildren = command.transactionIds.map { id -> requireTransaction(id, command.organizationId) }
        return SubTransactionGroup(parent = virtualParent, children = updatedChildren)
    }

    @Transactional
    override fun addToMerge(
        parentId: Long,
        transactionId: Long,
        organizationId: Long,
    ): SubTransactionGroup {
        val parent = requireTransaction(parentId, organizationId)
        require(parent.isVirtual && parent.parentId == null) { "Target is not a virtual merge parent" }

        val tx = requireTransaction(transactionId, organizationId)
        validateMergeCandidates(listOf(tx))
        require(tx.accountIban == parent.accountIban) {
            "Transaction must be from the same account as the merge group"
        }

        subTransactionPort.setParentId(transactionId, organizationId, parentId)
        subTransactionPort.setExcluded(transactionId, organizationId, true)

        val newAmount = parent.amount + tx.amount
        subTransactionPort.updateAmount(parentId, organizationId, newAmount)

        return buildGroup(parentId, organizationId)
    }

    @Transactional
    override fun removeFromMerge(
        transactionId: Long,
        organizationId: Long,
    ): SubTransactionGroup {
        val tx = requireTransaction(transactionId, organizationId)
        val parentId = requireNotNull(tx.parentId) { "Transaction $transactionId is not part of a merge group" }
        val parent = requireTransaction(parentId, organizationId)
        require(parent.isVirtual) { "Parent is not a virtual transaction" }

        val siblings = subTransactionPort.findChildren(parentId, organizationId)
        require(siblings.size > 2) {
            "Cannot remove: at least 2 transactions must remain in the merge group. Use unmerge to dissolve."
        }

        subTransactionPort.setParentId(transactionId, organizationId, null)
        subTransactionPort.setExcluded(transactionId, organizationId, false)

        val newAmount = parent.amount - tx.amount
        subTransactionPort.updateAmount(parentId, organizationId, newAmount)

        return buildGroup(parentId, organizationId)
    }

    @Transactional
    override fun unmergeTransactions(
        parentId: Long,
        organizationId: Long,
    ) {
        val parent = requireTransaction(parentId, organizationId)
        require(parent.isVirtual && parent.parentId == null) { "Transaction $parentId is not a virtual merge parent" }
        require(parent.offsetLinks.isEmpty()) {
            "Cannot unmerge: virtual parent has offset links (Verrechnungen)"
        }

        val children = subTransactionPort.findChildren(parentId, organizationId)
        for (child in children) {
            val childId = requireNotNull(child.id)
            subTransactionPort.setParentId(childId, organizationId, null)
            subTransactionPort.setExcluded(childId, organizationId, false)
        }

        subTransactionPort.deleteVirtual(parentId, organizationId)
    }

    override fun getGroup(
        transactionId: Long,
        organizationId: Long,
    ): SubTransactionGroup? {
        val tx = transactionRepository.findByIdAndOrganizationId(transactionId, organizationId) ?: return null
        return when {
            tx.isVirtual && tx.parentId == null -> buildGroup(transactionId, organizationId)
            tx.parentId != null -> buildGroup(tx.parentId, organizationId)
            else -> {
                val children = subTransactionPort.findChildren(transactionId, organizationId)
                if (children.isEmpty()) null else SubTransactionGroup(parent = tx, children = children)
            }
        }
    }

    private fun buildGroup(
        parentId: Long,
        organizationId: Long,
    ): SubTransactionGroup {
        val parent = requireTransaction(parentId, organizationId)
        val children = subTransactionPort.findChildren(parentId, organizationId)
        return SubTransactionGroup(parent = parent, children = children)
    }

    private fun requireTransaction(
        id: Long,
        organizationId: Long,
    ): Transaction =
        requireNotNull(transactionRepository.findByIdAndOrganizationId(id, organizationId)) {
            "Transaction $id not found"
        }

    private fun validateMergeCandidates(transactions: List<Transaction>) {
        for (tx in transactions) {
            require(!tx.isVirtual) { "Virtual transactions cannot be merged: ${tx.id}" }
            require(tx.parentId == null) { "Transaction ${tx.id} is already part of a merge group" }
            require(!tx.excluded) { "Transaction ${tx.id} is already excluded (part of a sub-transaction group)" }
            require(tx.offsetLinks.isEmpty()) {
                "Transaction ${tx.id} has offset links (Verrechnungen) and cannot be merged"
            }
        }
        val ibans = transactions.map { it.accountIban }.toSet()
        require(ibans.size == 1) { "All transactions must be from the same account (IBAN)" }
    }
}
