package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CreateOffsetLinkCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class TransactionOffsetPersistenceAdapterTest {
    private val offsetJpaRepository: TransactionOffsetJpaRepository = mock()
    private val transactionJpaRepository: TransactionJpaRepository = mock()
    private val adapter = TransactionOffsetPersistenceAdapter(offsetJpaRepository, transactionJpaRepository)

    private val organizationId = 1L
    private val date = LocalDate.of(2025, 1, 15)
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)
    private val accountEntity = AccountEntity(iban = "DE00TEST", name = "Test", organization = organizationEntity, id = 10L)

    @Test
    fun `should return null from delete when link not found`() {
        whenever(offsetJpaRepository.findByIdAndOrganizationId(99L, organizationId)).thenReturn(null)

        val result = adapter.delete(99L, organizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should return DeletedOffsetLink with groupId when deleted`() {
        val txA = txEntity(1L)
        val txB = txEntity(2L)
        val offsetEntity = TransactionOffsetEntity(transactionA = txA, transactionB = txB, groupId = 10L, id = 5L)
        whenever(offsetJpaRepository.findByIdAndOrganizationId(5L, organizationId)).thenReturn(offsetEntity)

        val result = adapter.delete(5L, organizationId)

        assertThat(result).isNotNull
        assertThat(result!!.groupId).isEqualTo(10L)
        verify(offsetJpaRepository).delete(offsetEntity)
    }

    @Test
    fun `should return DeletedOffsetLink with null groupId when offset has no group`() {
        val txA = txEntity(1L)
        val txB = txEntity(2L)
        val offsetEntity = TransactionOffsetEntity(transactionA = txA, transactionB = txB, groupId = null, id = 5L)
        whenever(offsetJpaRepository.findByIdAndOrganizationId(5L, organizationId)).thenReturn(offsetEntity)

        val result = adapter.delete(5L, organizationId)

        assertThat(result!!.groupId).isNull()
    }

    @Test
    fun `should skip updateLinksGroupId when memberIds is empty`() {
        adapter.updateLinksGroupId(1L, 2L, emptySet())

        verify(offsetJpaRepository, never()).updateGroupId(any(), any(), any())
    }

    @Test
    fun `should call updateGroupId when memberIds is non-empty`() {
        adapter.updateLinksGroupId(1L, 2L, setOf(5L, 6L))

        verify(offsetJpaRepository).updateGroupId(1L, 2L, setOf(5L, 6L))
    }

    @Test
    fun `should create offset link and return result`() {
        val txA = txEntity(1L)
        val txB = txEntity(2L)
        whenever(transactionJpaRepository.getReferenceById(1L)).thenReturn(txA)
        whenever(transactionJpaRepository.getReferenceById(2L)).thenReturn(txB)
        val saved =
            TransactionOffsetEntity(
                transactionA = txA,
                transactionB = txB,
                amountA = BigDecimal("-100"),
                amountB = BigDecimal("100"),
                groupId = 10L,
                id = 7L,
            )
        whenever(offsetJpaRepository.save(any())).thenReturn(saved)

        val command =
            CreateOffsetLinkCommand(
                transactionAId = 1L,
                transactionBId = 2L,
                amountA = BigDecimal("-100"),
                amountB = BigDecimal("100"),
                groupId = 10L,
            )
        val result = adapter.create(command)

        assertThat(result.id).isEqualTo(7L)
        assertThat(result.transactionAId).isEqualTo(1L)
        assertThat(result.transactionBId).isEqualTo(2L)
        assertThat(result.groupId).isEqualTo(10L)
    }

    @Test
    fun `should delegate existsByPair to repository`() {
        whenever(offsetJpaRepository.existsByNormalizedPair(1L, 2L)).thenReturn(true)

        val result = adapter.existsByPair(1L, 2L)

        assertThat(result).isTrue()
    }

    private fun txEntity(id: Long) =
        TransactionEntity(
            id = id,
            category = null,
            subcategory = null,
            bookingDate = date,
            valueDate = date,
            accountingDate = date,
            amount = BigDecimal("-100"),
            currency = "EUR",
            account = accountEntity,
            fingerprint = "fp$id",
            organization = organizationEntity,
        )
}
