package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TransactionOffsetJpaRepository : JpaRepository<TransactionOffsetEntity, Long> {
    @Query(
        """
        SELECT o FROM TransactionOffsetEntity o
        JOIN FETCH o.transactionA
        JOIN FETCH o.transactionB
        WHERE o.transactionA.id IN :ids OR o.transactionB.id IN :ids
        """,
    )
    fun findByTransactionIds(
        @Param("ids") ids: Collection<Long>,
    ): List<TransactionOffsetEntity>

    @Query(
        """
        SELECT o FROM TransactionOffsetEntity o
        JOIN FETCH o.transactionA
        JOIN FETCH o.transactionB
        WHERE o.id = :id
        AND (o.transactionA.organization.id = :organizationId OR o.transactionB.organization.id = :organizationId)
        """,
    )
    fun findByIdAndOrganizationId(
        @Param("id") id: Long,
        @Param("organizationId") organizationId: Long,
    ): TransactionOffsetEntity?

    @Query(
        """
        SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
        FROM TransactionOffsetEntity o
        WHERE o.transactionA.id = :txId OR o.transactionB.id = :txId
        """,
    )
    fun existsByTransactionId(
        @Param("txId") txId: Long,
    ): Boolean

    @Query(
        """
        SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
        FROM TransactionOffsetEntity o
        WHERE o.transactionA.id = :aId AND o.transactionB.id = :bId
        """,
    )
    fun existsByNormalizedPair(
        @Param("aId") aId: Long,
        @Param("bId") bId: Long,
    ): Boolean

    @Query(
        """
        SELECT o FROM TransactionOffsetEntity o
        JOIN FETCH o.transactionA
        JOIN FETCH o.transactionB
        WHERE o.groupId = :groupId
        """,
    )
    fun findByGroupId(
        @Param("groupId") groupId: Long,
    ): List<TransactionOffsetEntity>

    @Modifying
    @Query(
        """
        UPDATE TransactionOffsetEntity o SET o.groupId = :toGroupId
        WHERE o.groupId = :fromGroupId
        AND o.transactionA.id IN :ids
        AND o.transactionB.id IN :ids
        """,
    )
    fun updateGroupId(
        @Param("fromGroupId") fromGroupId: Long,
        @Param("toGroupId") toGroupId: Long,
        @Param("ids") ids: Collection<Long>,
    )

    @Query(
        """
        SELECT o FROM TransactionOffsetEntity o
        JOIN FETCH o.transactionA
        JOIN FETCH o.transactionB
        WHERE (o.transactionA.id = :txId OR o.transactionB.id = :txId)
        AND o.groupId = :groupId
        AND (o.transactionA.organization.id = :organizationId OR o.transactionB.organization.id = :organizationId)
        """,
    )
    fun findByTxAndGroupId(
        @Param("txId") txId: Long,
        @Param("groupId") groupId: Long,
        @Param("organizationId") organizationId: Long,
    ): List<TransactionOffsetEntity>

    @Modifying
    @Query(
        """
        UPDATE TransactionOffsetEntity o SET o.comment = :comment
        WHERE o.id = :id
        AND (o.transactionA.organization.id = :organizationId OR o.transactionB.organization.id = :organizationId)
        """,
    )
    fun updateComment(
        @Param("id") id: Long,
        @Param("organizationId") organizationId: Long,
        @Param("comment") comment: String?,
    )
}
