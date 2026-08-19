package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.TierRepository
import com.moneylytics.api.domain.Tier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TierServiceTest {
    private val tierRepository: TierRepository = mock()
    private val service = TierService(tierRepository)

    private val standardTier = Tier(id = 1L, name = "Standard", description = null, active = true, isDefault = true)
    private val proTier = Tier(id = 2L, name = "Pro", description = "Pro features", active = true, isDefault = false)

    @Test
    fun `should return all tiers`() {
        whenever(tierRepository.findAll()).thenReturn(listOf(standardTier, proTier))

        val result = service.listTiers()

        assertThat(result).containsExactly(standardTier, proTier)
    }

    @Test
    fun `should return user tier`() {
        whenever(tierRepository.findByUserId(42L)).thenReturn(standardTier)

        val result = service.getUserTier(42L)

        assertThat(result).isEqualTo(standardTier)
    }

    @Test
    fun `should call setDefault when creating a tier with isDefault true`() {
        val saved = proTier.copy(id = 3L, name = "Enterprise")
        whenever(tierRepository.save(org.mockito.kotlin.any())).thenReturn(saved)

        service.createTier("Enterprise", null, isDefault = true)

        verify(tierRepository).setDefault(3L)
    }

    @Test
    fun `should not call setDefault when creating a tier with isDefault false`() {
        val saved = proTier.copy(id = 3L, name = "Starter")
        whenever(tierRepository.save(org.mockito.kotlin.any())).thenReturn(saved)

        service.createTier("Starter", null, isDefault = false)

        verify(tierRepository, never()).setDefault(org.mockito.kotlin.any())
    }

    @Test
    fun `should throw TierNotFoundException when assigning non-existent tier`() {
        whenever(tierRepository.findById(99L)).thenReturn(null)

        assertThatThrownBy { service.assignTierToUser(1L, 99L) }
            .isInstanceOf(TierNotFoundException::class.java)
            .hasMessageContaining("99")
    }

    @Test
    fun `should assign tier to user when tier exists`() {
        whenever(tierRepository.findById(proTier.id)).thenReturn(proTier)

        service.assignTierToUser(1L, proTier.id)

        verify(tierRepository).assignToUser(1L, proTier.id)
    }
}
