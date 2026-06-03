package com.moneylytics.api.application.service

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
) : ManageTransactionOffsetUseCase {
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
}
