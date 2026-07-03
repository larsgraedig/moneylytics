package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.TransactionGroup

interface TransactionGroupRepository {
    fun findAllByUserId(userId: Long): List<TransactionGroup>

    fun findById(
        id: Long,
        userId: Long,
    ): TransactionGroup?

    fun create(userId: Long): TransactionGroup

    fun update(
        id: Long,
        userId: Long,
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
