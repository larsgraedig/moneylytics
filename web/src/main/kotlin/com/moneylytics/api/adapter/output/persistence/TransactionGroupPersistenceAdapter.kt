package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.TransactionGroupRepository
import com.moneylytics.api.domain.TransactionGroup
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionGroupPersistenceAdapter(
    private val groupJpaRepository: TransactionGroupJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val memberJpaRepository: TransactionGroupMemberJpaRepository,
) : TransactionGroupRepository {
    @Transactional(readOnly = true)
    override fun findAllByUserId(userId: Long): List<TransactionGroup> = groupJpaRepository.findAllByUserId(userId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findById(
        id: Long,
        userId: Long,
    ): TransactionGroup? = groupJpaRepository.findByIdAndUserId(id, userId)?.toDomain()

    @Transactional
    override fun create(userId: Long): TransactionGroup {
        val entity =
            groupJpaRepository.save(
                TransactionGroupEntity(user = userJpaRepository.getReferenceById(userId)),
            )
        return entity.toDomain()
    }

    @Transactional
    override fun update(
        id: Long,
        userId: Long,
        name: String?,
        comment: String?,
    ) {
        val entity = groupJpaRepository.findByIdAndUserId(id, userId) ?: return
        entity.name = name?.takeIf { it.isNotBlank() }
        entity.comment = comment?.takeIf { it.isNotBlank() }
        groupJpaRepository.save(entity)
    }

    @Transactional
    override fun delete(id: Long) {
        groupJpaRepository.deleteById(id)
    }

    @Transactional
    override fun addMember(
        groupId: Long,
        txId: Long,
    ) {
        val pk = TransactionGroupMemberEntity.PK(groupId = groupId, transactionId = txId)
        if (!memberJpaRepository.existsById(pk)) {
            memberJpaRepository.saveAndFlush(TransactionGroupMemberEntity(groupId = groupId, transactionId = txId))
        }
    }

    @Transactional
    override fun removeMember(
        groupId: Long,
        txId: Long,
    ) {
        memberJpaRepository.deleteByGroupIdAndTransactionId(groupId, txId)
    }

    @Transactional(readOnly = true)
    override fun memberCount(groupId: Long): Long = memberJpaRepository.countByGroupId(groupId)

    @Transactional(readOnly = true)
    override fun findGroupIdsForTransaction(txId: Long): List<Long> = memberJpaRepository.findGroupIdsByTransactionId(txId)

    @Transactional(readOnly = true)
    override fun findCommonGroupId(
        txAId: Long,
        txBId: Long,
    ): Long? = memberJpaRepository.findCommonGroupIds(txAId, txBId).firstOrNull()

    @Transactional(readOnly = true)
    override fun findMemberIds(groupId: Long): List<Long> = memberJpaRepository.findTransactionIdsByGroupId(groupId)

    private fun TransactionGroupEntity.toDomain() =
        TransactionGroup(
            id = requireNotNull(id),
            userId = user.id!!,
            name = name,
            comment = comment,
        )
}
