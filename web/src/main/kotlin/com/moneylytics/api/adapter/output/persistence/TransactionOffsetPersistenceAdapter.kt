package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CreateOffsetLinkCommand
import com.moneylytics.api.application.port.output.DeletedOffsetLink
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
                    amountA = command.amountA,
                    amountB = command.amountB,
                    groupId = command.groupId,
                ),
            )
        return OffsetLinkResult(
            id = requireNotNull(saved.id),
            transactionAId = command.transactionAId,
            transactionBId = command.transactionBId,
            amountA = command.amountA,
            amountB = command.amountB,
            groupId = command.groupId,
        )
    }

    @Transactional
    override fun delete(
        linkId: Long,
        userId: Long,
    ): DeletedOffsetLink? {
        val link = offsetJpaRepository.findByIdAndUserId(linkId, userId) ?: return null
        val groupId = link.groupId
        offsetJpaRepository.delete(link)
        return DeletedOffsetLink(groupId)
    }

    @Transactional(readOnly = true)
    override fun existsByPair(
        transactionAId: Long,
        transactionBId: Long,
    ): Boolean = offsetJpaRepository.existsByNormalizedPair(transactionAId, transactionBId)

    @Transactional(readOnly = true)
    override fun findLinksForGroup(groupId: Long): List<Pair<Long, Long>> =
        offsetJpaRepository.findByGroupId(groupId).map { it.transactionA.id!! to it.transactionB.id!! }

    @Transactional
    override fun updateLinksGroupId(
        fromGroupId: Long,
        toGroupId: Long,
        memberIds: Set<Long>,
    ) {
        if (memberIds.isEmpty()) return
        offsetJpaRepository.updateGroupId(fromGroupId, toGroupId, memberIds)
    }

    @Transactional
    override fun updateComment(
        linkId: Long,
        userId: Long,
        comment: String?,
    ) {
        offsetJpaRepository.updateComment(linkId, userId, comment)
    }
}
