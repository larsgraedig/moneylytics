package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.CollectionRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Collection
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class CollectionServiceTest {
    private val collectionRepository: CollectionRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val service = CollectionService(collectionRepository, transactionRepository)

    private val userId = 1L
    private val date = LocalDate.of(2025, 1, 1)
    private val urlaub = Collection(id = 1L, name = "Urlaub", note = "Sommer 2025")
    private val einkauf = Collection(id = 2L, name = "Einkauf", note = null)

    @Test
    fun `should return collections with their transactions`() {
        val tx = tx(id = 10L)
        whenever(collectionRepository.findAllByUserId(userId)).thenReturn(listOf(urlaub))
        whenever(collectionRepository.findTransactionIdsByCollectionId(1L, userId)).thenReturn(listOf(10L))
        whenever(transactionRepository.findByIdsAndUserId(setOf(10L), userId)).thenReturn(listOf(tx))

        val result = service.getCollections(userId)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[0].transactions).containsExactly(tx)
    }

    @Test
    fun `should return empty transactions when collection has no members`() {
        whenever(collectionRepository.findAllByUserId(userId)).thenReturn(listOf(einkauf))
        whenever(collectionRepository.findTransactionIdsByCollectionId(2L, userId)).thenReturn(emptyList())

        val result = service.getCollections(userId)

        assertThat(result[0].transactions).isEmpty()
        verify(transactionRepository, never()).findByIdsAndUserId(any(), any())
    }

    @Test
    fun `should return null when collection not found`() {
        whenever(collectionRepository.findByIdAndUserId(99L, userId)).thenReturn(null)

        val result = service.getCollection(99L, userId)

        assertThat(result).isNull()
    }

    @Test
    fun `should return collection with transactions when found`() {
        val tx = tx(id = 10L)
        whenever(collectionRepository.findByIdAndUserId(1L, userId)).thenReturn(urlaub)
        whenever(collectionRepository.findTransactionIdsByCollectionId(1L, userId)).thenReturn(listOf(10L))
        whenever(transactionRepository.findByIdsAndUserId(setOf(10L), userId)).thenReturn(listOf(tx))

        val result = service.getCollection(1L, userId)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("Urlaub")
        assertThat(result.transactions).containsExactly(tx)
    }

    @Test
    fun `should delegate create to repository`() {
        val newCollection = Collection(name = "Neu", note = null)
        val saved = Collection(id = 3L, name = "Neu", note = null)
        whenever(collectionRepository.create(newCollection, userId)).thenReturn(saved)

        val result = service.createCollection(newCollection, userId)

        assertThat(result).isEqualTo(saved)
    }

    @Test
    fun `should add and remove transactions from collection`() {
        service.addTransaction(1L, 10L, userId)
        service.removeTransaction(1L, 10L, userId)

        verify(collectionRepository).addTransaction(1L, 10L, userId)
        verify(collectionRepository).removeTransaction(1L, 10L, userId)
    }

    private fun tx(id: Long) =
        Transaction(
            id = id,
            category = null,
            subcategory = null,
            bookingDate = date,
            valueDate = date,
            accountingDate = date,
            amount = BigDecimal("-50.00"),
            currency = "EUR",
            accountIban = "DE00TEST",
        )
}
