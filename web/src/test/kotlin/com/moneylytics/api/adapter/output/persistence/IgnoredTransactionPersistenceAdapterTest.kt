package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IgnoredTransactionPersistenceAdapterTest {
    private val jpaRepository: IgnoredTransactionJpaRepository = mock()
    private val organizationJpaRepository: OrganizationJpaRepository = mock()
    private val adapter = IgnoredTransactionPersistenceAdapter(jpaRepository, organizationJpaRepository)

    private val organizationId = 1L
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)

    @Test
    fun `should return existing fingerprints as set`() {
        whenever(jpaRepository.findExistingFingerprints(listOf("fp1", "fp2"), organizationId)).thenReturn(listOf("fp1"))

        val result = adapter.findExistingFingerprints(listOf("fp1", "fp2"), organizationId)

        assertThat(result).containsExactly("fp1")
    }

    @Test
    fun `should save only fingerprints not already stored`() {
        whenever(jpaRepository.findExistingFingerprints(listOf("fp1", "fp2"), organizationId)).thenReturn(listOf("fp1"))
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)

        adapter.saveAll(listOf("fp1", "fp2"), organizationId)

        val captor = argumentCaptor<List<IgnoredTransactionEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(1)
        assertThat(captor.firstValue[0].fingerprint).isEqualTo("fp2")
    }

    @Test
    fun `should save nothing when all fingerprints already exist`() {
        whenever(jpaRepository.findExistingFingerprints(listOf("fp1"), organizationId)).thenReturn(listOf("fp1"))
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)

        adapter.saveAll(listOf("fp1"), organizationId)

        verify(jpaRepository, never()).saveAll(any<List<IgnoredTransactionEntity>>())
    }

    @Test
    fun `should save all fingerprints when none exist yet`() {
        whenever(jpaRepository.findExistingFingerprints(listOf("fp1", "fp2"), organizationId)).thenReturn(emptyList())
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)

        adapter.saveAll(listOf("fp1", "fp2"), organizationId)

        val captor = argumentCaptor<List<IgnoredTransactionEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(2)
    }

    @Test
    fun `should delegate deleteAll to repository`() {
        val fingerprints = listOf("fp1", "fp2")

        adapter.deleteAll(fingerprints, organizationId)

        verify(jpaRepository).deleteByFingerprintInAndOrganizationId(fingerprints, organizationId)
    }
}
