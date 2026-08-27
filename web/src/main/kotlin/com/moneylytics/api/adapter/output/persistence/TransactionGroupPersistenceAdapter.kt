package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.TransactionGroupRepository
import com.moneylytics.api.domain.TransactionGroup
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionGroupPersistenceAdapter(
    private val groupJpaRepository: TransactionGroupJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
    private val memberJpaRepository: TransactionGroupMemberJpaRepository,
) : TransactionGroupRepository {
    @Transactional(readOnly = true)
    override fun findAllByOrganizationId(organizationId: Long): List<TransactionGroup> =
        groupJpaRepository.findAllByOrganizationId(organizationId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findById(
        id: Long,
        organizationId: Long,
    ): TransactionGroup? = groupJpaRepository.findByIdAndOrganizationId(id, organizationId)?.toDomain()

    @Transactional
    override fun create(organizationId: Long): TransactionGroup {
        val entity =
            groupJpaRepository.save(
                TransactionGroupEntity(organization = organizationJpaRepository.getReferenceById(organizationId)),
            )
        return entity.toDomain()
    }

    @Transactional
    override fun update(
        id: Long,
        organizationId: Long,
        name: String?,
        comment: String?,
    ) {
        val entity = groupJpaRepository.findByIdAndOrganizationId(id, organizationId) ?: return
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
            organizationId = requireNotNull(organization.id),
            name = name,
            comment = comment,
        )
}
