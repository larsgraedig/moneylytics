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
    private val userJpaRepository: UserJpaRepository = mock()
    private val adapter = IgnoredTransactionPersistenceAdapter(jpaRepository, userJpaRepository)

    private val userId = 1L
    private val userEntity = UserEntity(externalId = "test@test.de", id = userId)

    @Test
    fun `should return existing fingerprints as set`() {
        whenever(jpaRepository.findExistingFingerprints(listOf("fp1", "fp2"), userId)).thenReturn(listOf("fp1"))

        val result = adapter.findExistingFingerprints(listOf("fp1", "fp2"), userId)

        assertThat(result).containsExactly("fp1")
    }

    @Test
    fun `should save only fingerprints not already stored`() {
        whenever(jpaRepository.findExistingFingerprints(listOf("fp1", "fp2"), userId)).thenReturn(listOf("fp1"))
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)

        adapter.saveAll(listOf("fp1", "fp2"), userId)

        val captor = argumentCaptor<List<IgnoredTransactionEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(1)
        assertThat(captor.firstValue[0].fingerprint).isEqualTo("fp2")
    }

    @Test
    fun `should save nothing when all fingerprints already exist`() {
        whenever(jpaRepository.findExistingFingerprints(listOf("fp1"), userId)).thenReturn(listOf("fp1"))
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)

        adapter.saveAll(listOf("fp1"), userId)

        verify(jpaRepository, never()).saveAll(any<List<IgnoredTransactionEntity>>())
    }

    @Test
    fun `should save all fingerprints when none exist yet`() {
        whenever(jpaRepository.findExistingFingerprints(listOf("fp1", "fp2"), userId)).thenReturn(emptyList())
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)

        adapter.saveAll(listOf("fp1", "fp2"), userId)

        val captor = argumentCaptor<List<IgnoredTransactionEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(2)
    }

    @Test
    fun `should delegate deleteAll to repository`() {
        val fingerprints = listOf("fp1", "fp2")

        adapter.deleteAll(fingerprints, userId)

        verify(jpaRepository).deleteByFingerprintInAndUserId(fingerprints, userId)
    }
}
