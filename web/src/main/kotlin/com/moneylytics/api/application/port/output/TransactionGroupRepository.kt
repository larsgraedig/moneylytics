package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.TransactionGroup

interface TransactionGroupRepository {
    fun findAllByOrganizationId(organizationId: Long): List<TransactionGroup>

    fun findById(
        id: Long,
        organizationId: Long,
    ): TransactionGroup?

    fun create(organizationId: Long): TransactionGroup

    fun update(
        id: Long,
        organizationId: Long,
        name: String?,
        comment: String?,
    )

    fun delete(id: Long)

    fun addMember(
        groupId: Long,
        txId: Long,
    )

    fun removeMember(
        groupId: Long,
        txId: Long,
    )

    fun memberCount(groupId: Long): Long

    fun findGroupIdsForTransaction(txId: Long): List<Long>

    fun findCommonGroupId(
        txAId: Long,
        txBId: Long,
    ): Long?

    fun findMemberIds(groupId: Long): List<Long>
}
