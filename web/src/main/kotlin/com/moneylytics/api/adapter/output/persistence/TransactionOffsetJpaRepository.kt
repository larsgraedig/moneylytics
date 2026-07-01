package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
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
        AND (o.transactionA.user.id = :userId OR o.transactionB.user.id = :userId)
        """,
    )
    fun findByIdAndUserId(
        @Param("id") id: Long,
        @Param("userId") userId: Long,
    ): TransactionOffsetEntity?

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
        WHERE o.transactionA.user.id = :userId
        """,
    )
    fun findAllByUserId(
        @Param("userId") userId: Long,
    ): List<TransactionOffsetEntity>
}
