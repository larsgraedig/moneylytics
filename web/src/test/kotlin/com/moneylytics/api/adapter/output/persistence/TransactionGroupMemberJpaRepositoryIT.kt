package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TransactionGroupMemberJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var groupRepo: TransactionGroupJpaRepository

    @Autowired private lateinit var memberRepo: TransactionGroupMemberJpaRepository

    private fun savedGroup() = groupRepo.save(TransactionGroupEntity(user = user))

    private fun savedMember(
        groupId: Long,
        transactionId: Long,
    ) = memberRepo.save(TransactionGroupMemberEntity(groupId = groupId, transactionId = transactionId))

    @Test
    fun `should find members by transaction ids`() {
        val group = savedGroup()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx3 = savedTransaction("fp-3")
        savedMember(group.id!!, tx1.id!!)
        savedMember(group.id!!, tx2.id!!)

        val result = memberRepo.findByTransactionIds(listOf(tx1.id!!, tx3.id!!))

        assertThat(result).hasSize(1)
        assertThat(result.first().transactionId).isEqualTo(tx1.id)
    }

    @Test
    fun `should find group ids for transaction`() {
        val group1 = savedGroup()
        val group2 = savedGroup()
        val tx = savedTransaction("fp-1")
        savedMember(group1.id!!, tx.id!!)
        savedMember(group2.id!!, tx.id!!)

        val result = memberRepo.findGroupIdsByTransactionId(tx.id!!)

        assertThat(result).containsExactlyInAnyOrder(group1.id, group2.id)
    }

    @Test
    fun `should find transaction ids in group`() {
        val group = savedGroup()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedMember(group.id!!, tx1.id!!)
        savedMember(group.id!!, tx2.id!!)

        val result = memberRepo.findTransactionIdsByGroupId(group.id!!)

        assertThat(result).containsExactlyInAnyOrder(tx1.id, tx2.id)
    }

    @Test
    fun `should find common group ids for two transactions`() {
        val sharedGroup = savedGroup()
        val onlyGroup1 = savedGroup()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedMember(sharedGroup.id!!, tx1.id!!)
        savedMember(sharedGroup.id!!, tx2.id!!)
        savedMember(onlyGroup1.id!!, tx1.id!!)

        val result = memberRepo.findCommonGroupIds(tx1.id!!, tx2.id!!)

        assertThat(result).containsExactly(sharedGroup.id)
    }

    @Test
    fun `should return empty list when transactions share no group`() {
        val group1 = savedGroup()
        val group2 = savedGroup()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedMember(group1.id!!, tx1.id!!)
        savedMember(group2.id!!, tx2.id!!)

        val result = memberRepo.findCommonGroupIds(tx1.id!!, tx2.id!!)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should count members in group`() {
        val group = savedGroup()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedMember(group.id!!, tx1.id!!)
        savedMember(group.id!!, tx2.id!!)

        val count = memberRepo.countByGroupId(group.id!!)

        assertThat(count).isEqualTo(2L)
    }

    @Test
    fun `should delete all members by group id`() {
        val group = savedGroup()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedMember(group.id!!, tx1.id!!)
        savedMember(group.id!!, tx2.id!!)
        flushAndClear()

        memberRepo.deleteByGroupId(group.id!!)
        flushAndClear()

        assertThat(memberRepo.countByGroupId(group.id!!)).isEqualTo(0L)
    }

    @Test
    fun `should delete specific member by group id and transaction id`() {
        val group = savedGroup()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedMember(group.id!!, tx1.id!!)
        savedMember(group.id!!, tx2.id!!)
        flushAndClear()

        memberRepo.deleteByGroupIdAndTransactionId(group.id!!, tx1.id!!)
        flushAndClear()

        assertThat(memberRepo.countByGroupId(group.id!!)).isEqualTo(1L)
        assertThat(memberRepo.findTransactionIdsByGroupId(group.id!!)).containsExactly(tx2.id)
    }
}
