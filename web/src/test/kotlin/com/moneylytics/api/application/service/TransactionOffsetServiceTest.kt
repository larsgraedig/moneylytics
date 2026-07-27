package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.AllocationExceededException
import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.output.CreateOffsetLinkCommand
import com.moneylytics.api.application.port.output.DeletedOffsetLink
import com.moneylytics.api.application.port.output.OffsetLinkResult
import com.moneylytics.api.application.port.output.TransactionGroupRepository
import com.moneylytics.api.application.port.output.TransactionOffsetRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import com.moneylytics.api.domain.TransactionGroup
import com.moneylytics.api.domain.TransactionOffsetLink
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class TransactionOffsetServiceTest {
    private val offsetRepository: TransactionOffsetRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val groupRepository: TransactionGroupRepository = mock()
    private val service = TransactionOffsetService(offsetRepository, transactionRepository, groupRepository)

    private val organizationId = 1L
    private val date = LocalDate.of(2025, 1, 15)
    private val newGroup = TransactionGroup(id = 10L, organizationId = organizationId, name = null, comment = null)

    @Test
    fun `should throw when linking transaction to itself`() {
        val command = command(transactionId = 5L, otherTransactionId = 5L)

        assertThatThrownBy { service.linkTransactions(command) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should throw when source transaction not found`() {
        val command = command(transactionId = 1L, otherTransactionId = 2L)
        whenever(transactionRepository.findByIdAndOrganizationId(1L, organizationId)).thenReturn(null)

        assertThatThrownBy { service.linkTransactions(command) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should throw when other transaction not found`() {
        val command = command(transactionId = 1L, otherTransactionId = 2L)
        whenever(transactionRepository.findByIdAndOrganizationId(1L, organizationId)).thenReturn(expense(1L))
        whenever(transactionRepository.findByIdAndOrganizationId(2L, organizationId)).thenReturn(null)

        assertThatThrownBy { service.linkTransactions(command) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should throw when transactions are already linked`() {
        val command = command(transactionId = 1L, otherTransactionId = 2L)
        whenever(transactionRepository.findByIdAndOrganizationId(1L, organizationId)).thenReturn(expense(1L))
        whenever(transactionRepository.findByIdAndOrganizationId(2L, organizationId)).thenReturn(expense(2L))
        whenever(offsetRepository.existsByPair(1L, 2L)).thenReturn(true)

        assertThatThrownBy { service.linkTransactions(command) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `should not create offset link for same-sign transactions without custom amounts`() {
        val txA = expense(1L, amount = BigDecimal("-100"))
        val txB = expense(2L, amount = BigDecimal("-50"))
        val command = command(1L, 2L, myAmount = null, otherAmount = null)
        stubSuccessfulLink(1L, 2L, txA, txB)
        whenever(groupRepository.findGroupIdsForTransaction(1L)).thenReturn(emptyList())
        whenever(groupRepository.findGroupIdsForTransaction(2L)).thenReturn(emptyList())
        whenever(groupRepository.create(organizationId)).thenReturn(newGroup)

        service.linkTransactions(command)

        verify(offsetRepository, never()).create(any())
    }

    @Test
    fun `should create offset link for opposite-sign transactions`() {
        val txA = expense(1L, amount = BigDecimal("-100"))
        val txB = income(2L, amount = BigDecimal("50"))
        val command = command(1L, 2L, myAmount = null, otherAmount = null)
        stubSuccessfulLink(1L, 2L, txA, txB)
        whenever(groupRepository.findGroupIdsForTransaction(1L)).thenReturn(emptyList())
        whenever(groupRepository.findGroupIdsForTransaction(2L)).thenReturn(emptyList())
        whenever(groupRepository.create(organizationId)).thenReturn(newGroup)
        whenever(offsetRepository.create(any())).thenReturn(OffsetLinkResult(1L, 1L, 2L, BigDecimal("-100"), BigDecimal("50"), 10L))

        service.linkTransactions(command)

        verify(offsetRepository).create(
            CreateOffsetLinkCommand(
                transactionAId = 1L,
                transactionBId = 2L,
                amountA = BigDecimal("-100"),
                amountB = BigDecimal("50"),
                groupId = 10L,
            ),
        )
    }

    @Test
    fun `should join existing group of source transaction when no target group specified`() {
        val txA = expense(1L)
        val txB = expense(2L)
        val command = command(1L, 2L, myAmount = null, otherAmount = null)
        stubSuccessfulLink(1L, 2L, txA, txB)
        whenever(groupRepository.findGroupIdsForTransaction(1L)).thenReturn(listOf(7L))

        service.linkTransactions(command)

        verify(groupRepository).addMember(7L, 2L)
    }

    @Test
    fun `should use targetGroupId when provided`() {
        val txA = expense(1L)
        val txB = expense(2L)
        val command = command(1L, 2L, targetGroupId = 55L)
        stubSuccessfulLink(1L, 2L, txA, txB)

        service.linkTransactions(command)

        verify(groupRepository).addMember(55L, 1L)
        verify(groupRepository).addMember(55L, 2L)
    }

    @Test
    fun `should force new group when forceNewGroup is true`() {
        val txA = expense(1L)
        val txB = expense(2L)
        val command = command(1L, 2L, forceNewGroup = true)
        stubSuccessfulLink(1L, 2L, txA, txB)
        whenever(groupRepository.create(organizationId)).thenReturn(newGroup)

        service.linkTransactions(command)

        verify(groupRepository).create(organizationId)
    }

    @Test
    fun `should throw AllocationExceededException when committed amount would exceed transaction total`() {
        val existingLink = offsetLink(amountA = BigDecimal("-80"), amountB = BigDecimal("80"), linkedAmount = BigDecimal("80"))
        val txA = expense(1L, amount = BigDecimal("-100"), offsetLinks = listOf(existingLink))
        val txB = income(2L, amount = BigDecimal("50"))
        val command = command(1L, 2L, myAmount = null, otherAmount = null)
        whenever(transactionRepository.findByIdAndOrganizationId(1L, organizationId)).thenReturn(txA)
        whenever(transactionRepository.findByIdAndOrganizationId(2L, organizationId)).thenReturn(txB)
        whenever(offsetRepository.existsByPair(1L, 2L)).thenReturn(false)

        assertThatThrownBy { service.linkTransactions(command) }
            .isInstanceOf(AllocationExceededException::class.java)
    }

    @Test
    fun `should return true when unlinking existing link`() {
        whenever(offsetRepository.delete(5L, organizationId)).thenReturn(DeletedOffsetLink(groupId = null))

        val result = service.unlinkTransactions(5L, organizationId)

        assertThat(result).isTrue()
    }

    @Test
    fun `should return false when link not found`() {
        whenever(offsetRepository.delete(99L, organizationId)).thenReturn(null)

        val result = service.unlinkTransactions(99L, organizationId)

        assertThat(result).isFalse()
    }

    @Test
    fun `should delete group when it becomes empty after unlink`() {
        whenever(offsetRepository.delete(5L, organizationId)).thenReturn(DeletedOffsetLink(groupId = 10L))
        whenever(groupRepository.findMemberIds(10L)).thenReturn(emptyList())

        service.unlinkTransactions(5L, organizationId)

        verify(groupRepository).delete(10L)
    }

    @Test
    fun `should return empty list when no groups exist`() {
        whenever(groupRepository.findAllByOrganizationId(organizationId)).thenReturn(emptyList())

        val result = service.getLinkedGroups(organizationId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should return linked groups with their transactions`() {
        val group = TransactionGroup(id = 10L, organizationId = organizationId, name = "Rückerstattung", comment = null)
        val txA = expense(1L)
        val txB = income(2L)
        whenever(groupRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(group))
        whenever(groupRepository.findMemberIds(10L)).thenReturn(listOf(1L, 2L))
        whenever(transactionRepository.findByIdsAndOrganizationId(setOf(1L, 2L), organizationId)).thenReturn(listOf(txA, txB))

        val result = service.getLinkedGroups(organizationId)

        assertThat(result).hasSize(1)
        assertThat(result[0].groupId).isEqualTo(10L)
        assertThat(result[0].name).isEqualTo("Rückerstattung")
        assertThat(result[0].transactions).hasSize(2)
    }

    @Test
    fun `should return null when group not found in getLinkedGroup`() {
        whenever(groupRepository.findById(99L, organizationId)).thenReturn(null)

        val result = service.getLinkedGroup(99L, organizationId)

        assertThat(result).isNull()
    }

    private fun stubSuccessfulLink(
        idA: Long,
        idB: Long,
        txA: Transaction,
        txB: Transaction,
    ) {
        whenever(transactionRepository.findByIdAndOrganizationId(idA, organizationId)).thenReturn(txA)
        whenever(transactionRepository.findByIdAndOrganizationId(idB, organizationId)).thenReturn(txB)
        whenever(offsetRepository.existsByPair(idA, idB)).thenReturn(false)
    }

    private fun command(
        transactionId: Long,
        otherTransactionId: Long,
        myAmount: BigDecimal? = null,
        otherAmount: BigDecimal? = null,
        targetGroupId: Long? = null,
        forceNewGroup: Boolean = false,
    ) = LinkTransactionsCommand(
        transactionId = transactionId,
        otherTransactionId = otherTransactionId,
        myAmount = myAmount,
        otherAmount = otherAmount,
        organizationId = organizationId,
        targetGroupId = targetGroupId,
        forceNewGroup = forceNewGroup,
    )

    private fun expense(
        id: Long,
        amount: BigDecimal = BigDecimal("-100"),
        offsetLinks: List<TransactionOffsetLink> = emptyList(),
    ) = Transaction(
        id = id,
        category = "Wohnen",
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00TEST",
        offsetLinks = offsetLinks,
    )

    private fun income(
        id: Long,
        amount: BigDecimal = BigDecimal("100"),
    ) = Transaction(
        id = id,
        category = "Einnahmen",
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00TEST",
    )

    private fun offsetLink(
        amountA: BigDecimal?,
        amountB: BigDecimal?,
        linkedAmount: BigDecimal,
    ) = TransactionOffsetLink(
        id = 5L,
        linkedTransactionId = 99L,
        linkedTransactionAmount = linkedAmount,
        amountA = amountA,
        amountB = amountB,
        myCommitted = amountA ?: BigDecimal.ZERO,
        groupId = 7L,
    )
}
