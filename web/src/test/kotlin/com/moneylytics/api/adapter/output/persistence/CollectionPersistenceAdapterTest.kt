package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.Collection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CollectionPersistenceAdapterTest {
    private val collectionJpaRepository: CollectionJpaRepository = mock()
    private val collectionTransactionJpaRepository: CollectionTransactionJpaRepository = mock()
    private val organizationJpaRepository: OrganizationJpaRepository = mock()
    private val adapter =
        CollectionPersistenceAdapter(collectionJpaRepository, collectionTransactionJpaRepository, organizationJpaRepository)

    private val organizationId = 1L
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)

    @Test
    fun `should map entity to domain collection`() {
        val entity = CollectionEntity(organization = organizationEntity, name = "Urlaub", note = "Sommer", id = 1L)
        whenever(collectionJpaRepository.findByOrganizationId(organizationId)).thenReturn(listOf(entity))

        val result = adapter.findAllByOrganizationId(organizationId)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[0].name).isEqualTo("Urlaub")
        assertThat(result[0].note).isEqualTo("Sommer")
    }

    @Test
    fun `should return null when collection not found`() {
        whenever(collectionJpaRepository.findByIdAndOrganizationId(99L, organizationId)).thenReturn(null)

        val result = adapter.findByIdAndOrganizationId(99L, organizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should add transaction when not already member of collection`() {
        whenever(collectionTransactionJpaRepository.existsByCollectionIdAndTransactionId(1L, 10L)).thenReturn(false)

        adapter.addTransaction(1L, 10L, organizationId)

        val captor = argumentCaptor<CollectionTransactionEntity>()
        verify(collectionTransactionJpaRepository).save(captor.capture())
        assertThat(captor.firstValue.collectionId).isEqualTo(1L)
        assertThat(captor.firstValue.transactionId).isEqualTo(10L)
    }

    @Test
    fun `should skip saving transaction when already member of collection`() {
        whenever(collectionTransactionJpaRepository.existsByCollectionIdAndTransactionId(1L, 10L)).thenReturn(true)

        adapter.addTransaction(1L, 10L, organizationId)

        verify(collectionTransactionJpaRepository, never()).save(any())
    }

    @Test
    fun `should update collection name and note`() {
        val entity = CollectionEntity(organization = organizationEntity, name = "Old", note = null, id = 1L)
        val updated = CollectionEntity(organization = organizationEntity, name = "Neu", note = "Notiz", id = 1L)
        whenever(collectionJpaRepository.findByOrganizationId(organizationId)).thenReturn(listOf(entity))
        whenever(collectionJpaRepository.save(entity)).thenReturn(updated)

        val result = adapter.update(Collection(id = 1L, name = "Neu", note = "Notiz"), organizationId)

        assertThat(result.name).isEqualTo("Neu")
        assertThat(result.note).isEqualTo("Notiz")
    }

    @Test
    fun `should delegate removeTransaction to repository`() {
        adapter.removeTransaction(1L, 10L, organizationId)

        verify(collectionTransactionJpaRepository).deleteByCollectionIdAndTransactionId(1L, 10L)
    }
}
