package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.IgnoredTransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IgnoredTransactionServiceTest {
    private val repository: IgnoredTransactionRepository = mock()
    private val service = IgnoredTransactionService(repository)

    private val userId = 1L

    @Test
    fun `should return empty set without calling repository when fingerprints list is empty`() {
        val result = service.findIgnoredFingerprints(emptyList(), userId)

        assertThat(result).isEmpty()
        verify(repository, never()).findExistingFingerprints(any(), any())
    }

    @Test
    fun `should delegate to repository when fingerprints list is non-empty`() {
        val fingerprints = listOf("fp1", "fp2")
        whenever(repository.findExistingFingerprints(fingerprints, userId)).thenReturn(setOf("fp1"))

        val result = service.findIgnoredFingerprints(fingerprints, userId)

        assertThat(result).containsExactly("fp1")
    }

    @Test
    fun `should save toIgnore and delete toUnignore when both are present`() {
        val toIgnore = listOf("fp1", "fp2")
        val toUnignore = listOf("fp3")

        service.update(toIgnore, toUnignore, userId)

        verify(repository).saveAll(toIgnore, userId)
        verify(repository).deleteAll(toUnignore, userId)
    }

    @Test
    fun `should skip saveAll when toIgnore is empty`() {
        service.update(emptyList(), listOf("fp1"), userId)

        verify(repository, never()).saveAll(any(), any())
        verify(repository).deleteAll(listOf("fp1"), userId)
    }

    @Test
    fun `should skip deleteAll when toUnignore is empty`() {
        service.update(listOf("fp1"), emptyList(), userId)

        verify(repository).saveAll(listOf("fp1"), userId)
        verify(repository, never()).deleteAll(any(), any())
    }
}
