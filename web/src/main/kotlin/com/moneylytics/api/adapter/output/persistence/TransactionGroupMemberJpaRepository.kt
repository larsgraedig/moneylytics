package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TransactionGroupMemberJpaRepository : JpaRepository<TransactionGroupMemberEntity, TransactionGroupMemberEntity.PK> {
    @Query("SELECT m.groupId FROM TransactionGroupMemberEntity m WHERE m.transactionId = :txId")
    fun findGroupIdsByTransactionId(
        @Param("txId") txId: Long,
    ): List<Long>

    @Query("SELECT m.transactionId FROM TransactionGroupMemberEntity m WHERE m.groupId = :groupId")
    fun findTransactionIdsByGroupId(
        @Param("groupId") groupId: Long,
    ): List<Long>

    @Query(
        """
        SELECT m.groupId FROM TransactionGroupMemberEntity m
        WHERE m.transactionId = :txAId
        AND m.groupId IN (SELECT m2.groupId FROM TransactionGroupMemberEntity m2 WHERE m2.transactionId = :txBId)
        """,
    )
    fun findCommonGroupIds(
        @Param("txAId") txAId: Long,
        @Param("txBId") txBId: Long,
    ): List<Long>

    fun deleteByGroupId(groupId: Long)

    fun deleteByGroupIdAndTransactionId(
        groupId: Long,
        transactionId: Long,
    )

    fun countByGroupId(groupId: Long): Long
}
