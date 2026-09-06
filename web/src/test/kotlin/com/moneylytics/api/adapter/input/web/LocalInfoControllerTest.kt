package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.Role
import com.moneylytics.api.domain.Tier
import com.moneylytics.api.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LocalInfoControllerTest {
    private val userRepository: UserRepository = mock()
    private val controller = LocalInfoController(userRepository)

    private val tier = Tier(id = 1L, name = "Standard", description = null, active = true, isDefault = true)

    private fun existingUser(externalId: String) =
        User(id = 1L, externalId = externalId, passwordHash = null, role = Role.USER, tier = tier)

    @Test
    fun `should return only demo users that exist in the database`() {
        whenever(userRepository.findByExternalId(any())).thenReturn(null)
        whenever(userRepository.findByExternalId("dev@local.dev")).thenReturn(existingUser("dev@local.dev"))

        val result = controller.localInfo()

        assertThat(result).extracting<String> { it.username }.containsExactly("dev@local.dev")
    }

    @Test
    fun `should return empty list when no demo users exist`() {
        whenever(userRepository.findByExternalId(any())).thenReturn(null)

        val result = controller.localInfo()

        assertThat(result).isEmpty()
    }

    @Test
    fun `should return full list when all demo users exist`() {
        whenever(userRepository.findByExternalId(any())).thenAnswer { existingUser(it.getArgument(0)) }

        val result = controller.localInfo()

        assertThat(result).hasSize(10)
    }
}
