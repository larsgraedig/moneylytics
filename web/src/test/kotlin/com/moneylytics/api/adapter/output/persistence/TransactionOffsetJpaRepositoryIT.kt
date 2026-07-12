package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class TransactionOffsetJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var offsetRepo: TransactionOffsetJpaRepository

    private fun savedOffset(
        txA: TransactionEntity,
        txB: TransactionEntity,
        groupId: Long? = null,
        comment: String? = null,
    ) = offsetRepo.save(
        TransactionOffsetEntity(
            transactionA = txA,
            transactionB = txB,
            amountA = BigDecimal("50.00"),
            amountB = BigDecimal("50.00"),
            groupId = groupId,
            comment = comment,
        ),
    )

    @Test
    fun `should find offsets by transaction ids`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx3 = savedTransaction("fp-3")
        val tx4 = savedTransaction("fp-4")
        savedOffset(tx1, tx2)

        val result = offsetRepo.findByTransactionIds(listOf(tx1.id!!, tx3.id!!, tx4.id!!))

        assertThat(result).hasSize(1)
        assertThat(result.first().transactionA.fingerprint).isEqualTo("fp-1")
    }

    @Test
    fun `should find offset matching either side of the pair`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedOffset(tx1, tx2)

        val byA = offsetRepo.findByTransactionIds(listOf(tx1.id!!))
        val byB = offsetRepo.findByTransactionIds(listOf(tx2.id!!))

        assertThat(byA).hasSize(1)
        assertThat(byB).hasSize(1)
    }

    @Test
    fun `should find offset by id when user owns one of the transactions`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val offset = savedOffset(tx1, tx2)

        val result = offsetRepo.findByIdAndUserId(offset.id!!, user.id!!)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(offset.id)
    }

    @Test
    fun `should return null when offset does not belong to user`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER00000000002", name = "Fremd", user = otherUser))
        val tx1 = savedTransaction("fp-1", forAccount = otherAccount, forUser = otherUser)
        val tx2 = savedTransaction("fp-2", forAccount = otherAccount, forUser = otherUser)
        val offset = savedOffset(tx1, tx2)

        val result = offsetRepo.findByIdAndUserId(offset.id!!, user.id!!)

        assertThat(result).isNull()
    }

    @Test
    fun `should detect existing pair in normalized order`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedOffset(tx1, tx2)

        assertThat(offsetRepo.existsByNormalizedPair(tx1.id!!, tx2.id!!)).isTrue()
    }

    @Test
    fun `should return false when pair does not exist`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")

        assertThat(offsetRepo.existsByNormalizedPair(tx1.id!!, tx2.id!!)).isFalse()
    }

    @Test
    fun `should find all offsets by group id`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx3 = savedTransaction("fp-3")
        val tx4 = savedTransaction("fp-4")
        savedOffset(tx1, tx2, groupId = 42L)
        savedOffset(tx3, tx4, groupId = 99L)

        val result = offsetRepo.findByGroupId(42L)

        assertThat(result).hasSize(1)
        assertThat(result.first().groupId).isEqualTo(42L)
    }

    @Test
    fun `should update group id for offsets`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val offset = savedOffset(tx1, tx2, groupId = 1L)
        flushAndClear()

        offsetRepo.updateGroupId(fromGroupId = 1L, toGroupId = 2L, ids = listOf(tx1.id!!, tx2.id!!))
        flushAndClear()

        val updated = offsetRepo.findById(offset.id!!).get()
        assertThat(updated.groupId).isEqualTo(2L)
    }

    @Test
    fun `should find offsets by transaction and group id`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedOffset(tx1, tx2, groupId = 5L)

        val result = offsetRepo.findByTxAndGroupId(txId = tx1.id!!, groupId = 5L, userId = user.id!!)

        assertThat(result).hasSize(1)
        assertThat(result.first().transactionA.fingerprint).isEqualTo("fp-1")
    }

    @Test
    fun `should update comment on offset`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val offset = savedOffset(tx1, tx2)
        flushAndClear()

        offsetRepo.updateComment(id = offset.id!!, userId = user.id!!, comment = "Arztkosten erstattet")
        flushAndClear()

        val updated = offsetRepo.findById(offset.id!!).get()
        assertThat(updated.comment).isEqualTo("Arztkosten erstattet")
    }

    @Test
    fun `should not update comment on offset belonging to other user`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER00000000002", name = "Fremd", user = otherUser))
        val tx1 = savedTransaction("fp-1", forAccount = otherAccount, forUser = otherUser)
        val tx2 = savedTransaction("fp-2", forAccount = otherAccount, forUser = otherUser)
        val offset = savedOffset(tx1, tx2)
        flushAndClear()

        offsetRepo.updateComment(id = offset.id!!, userId = user.id!!, comment = "Unerlaubt")
        flushAndClear()

        val unchanged = offsetRepo.findById(offset.id!!).get()
        assertThat(unchanged.comment).isNull()
    }
}
