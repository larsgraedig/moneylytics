package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TransactionGroupMemberJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var groupRepo: TransactionGroupJpaRepository

    @Autowired private lateinit var memberRepo: TransactionGroupMemberJpaRepository

    private fun savedGroupId() = checkNotNull(groupRepo.save(TransactionGroupEntity(user = user)).id)

    private fun savedMember(
        groupId: Long,
        transactionId: Long,
    ) = memberRepo.save(TransactionGroupMemberEntity(groupId = groupId, transactionId = transactionId))

    @Test
    fun `should find members by transaction ids`() {
        val groupId = savedGroupId()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx3 = savedTransaction("fp-3")
        val tx1Id = checkNotNull(tx1.id)
        val tx3Id = checkNotNull(tx3.id)
        savedMember(groupId, tx1Id)
        savedMember(groupId, checkNotNull(tx2.id))

        val result = memberRepo.findByTransactionIds(listOf(tx1Id, tx3Id))

        assertThat(result).hasSize(1)
        assertThat(result.first().transactionId).isEqualTo(tx1Id)
    }

    @Test
    fun `should find group ids for transaction`() {
        val groupId1 = savedGroupId()
        val groupId2 = savedGroupId()
        val tx = savedTransaction("fp-1")
        val txId = checkNotNull(tx.id)
        savedMember(groupId1, txId)
        savedMember(groupId2, txId)

        val result = memberRepo.findGroupIdsByTransactionId(txId)

        assertThat(result).containsExactlyInAnyOrder(groupId1, groupId2)
    }

    @Test
    fun `should find transaction ids in group`() {
        val groupId = savedGroupId()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx1Id = checkNotNull(tx1.id)
        val tx2Id = checkNotNull(tx2.id)
        savedMember(groupId, tx1Id)
        savedMember(groupId, tx2Id)

        val result = memberRepo.findTransactionIdsByGroupId(groupId)

        assertThat(result).containsExactlyInAnyOrder(tx1Id, tx2Id)
    }

    @Test
    fun `should find common group ids for two transactions`() {
        val sharedGroupId = savedGroupId()
        val onlyGroupId = savedGroupId()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx1Id = checkNotNull(tx1.id)
        val tx2Id = checkNotNull(tx2.id)
        savedMember(sharedGroupId, tx1Id)
        savedMember(sharedGroupId, tx2Id)
        savedMember(onlyGroupId, tx1Id)

        val result = memberRepo.findCommonGroupIds(tx1Id, tx2Id)

        assertThat(result).containsExactly(sharedGroupId)
    }

    @Test
    fun `should return empty list when transactions share no group`() {
        val groupId1 = savedGroupId()
        val groupId2 = savedGroupId()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx1Id = checkNotNull(tx1.id)
        val tx2Id = checkNotNull(tx2.id)
        savedMember(groupId1, tx1Id)
        savedMember(groupId2, tx2Id)

        val result = memberRepo.findCommonGroupIds(tx1Id, tx2Id)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should count members in group`() {
        val groupId = savedGroupId()
        val tx1Id = checkNotNull(savedTransaction("fp-1").id)
        val tx2Id = checkNotNull(savedTransaction("fp-2").id)
        savedMember(groupId, tx1Id)
        savedMember(groupId, tx2Id)

        val count = memberRepo.countByGroupId(groupId)

        assertThat(count).isEqualTo(2L)
    }

    @Test
    fun `should delete all members by group id`() {
        val groupId = savedGroupId()
        val tx1Id = checkNotNull(savedTransaction("fp-1").id)
        val tx2Id = checkNotNull(savedTransaction("fp-2").id)
        savedMember(groupId, tx1Id)
        savedMember(groupId, tx2Id)
        flushAndClear()

        memberRepo.deleteByGroupId(groupId)
        flushAndClear()

        assertThat(memberRepo.countByGroupId(groupId)).isEqualTo(0L)
    }

    @Test
    fun `should delete specific member by group id and transaction id`() {
        val groupId = savedGroupId()
        val tx1Id = checkNotNull(savedTransaction("fp-1").id)
        val tx2Id = checkNotNull(savedTransaction("fp-2").id)
        savedMember(groupId, tx1Id)
        savedMember(groupId, tx2Id)
        flushAndClear()

        memberRepo.deleteByGroupIdAndTransactionId(groupId, tx1Id)
        flushAndClear()

        assertThat(memberRepo.countByGroupId(groupId)).isEqualTo(1L)
        assertThat(memberRepo.findTransactionIdsByGroupId(groupId)).containsExactly(tx2Id)
    }
}
