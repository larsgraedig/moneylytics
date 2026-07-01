package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CreateOffsetLinkCommand
import com.moneylytics.api.application.port.output.OffsetLinkResult
import com.moneylytics.api.application.port.output.TransactionOffsetRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionOffsetPersistenceAdapter(
    private val offsetJpaRepository: TransactionOffsetJpaRepository,
    private val transactionJpaRepository: TransactionJpaRepository,
) : TransactionOffsetRepository {
    @Transactional
    override fun create(command: CreateOffsetLinkCommand): OffsetLinkResult {
        val saved =
            offsetJpaRepository.save(
                TransactionOffsetEntity(
                    transactionA = transactionJpaRepository.getReferenceById(command.transactionAId),
                    transactionB = transactionJpaRepository.getReferenceById(command.transactionBId),
                    amount = command.partialAmount,
                ),
            )
        return OffsetLinkResult(
            id = requireNotNull(saved.id),
            transactionAId = command.transactionAId,
            transactionBId = command.transactionBId,
            partialAmount = command.partialAmount,
        )
    }

    @Transactional
    override fun delete(
        linkId: Long,
        userId: Long,
    ): Boolean {
        val link = offsetJpaRepository.findByIdAndUserId(linkId, userId) ?: return false
        offsetJpaRepository.delete(link)
        return true
    }

    @Transactional(readOnly = true)
    override fun existsByPair(
        transactionAId: Long,
        transactionBId: Long,
    ): Boolean = offsetJpaRepository.existsByNormalizedPair(transactionAId, transactionBId)

    @Transactional(readOnly = true)
    override fun findIdPairsForUser(userId: Long): List<Pair<Long, Long>> =
        offsetJpaRepository.findAllByUserId(userId).map { it.transactionA.id!! to it.transactionB.id!! }
}
