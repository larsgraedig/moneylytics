package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.RegisterUserUseCase
import com.moneylytics.api.application.port.output.OrganizationRepository
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.OrgRole
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class OrganizationServiceTest {
    private val organizationRepository: OrganizationRepository = mock()
    private val userRepository: UserRepository = mock()
    private val registerUserUseCase: RegisterUserUseCase = mock()
    private val service = OrganizationService(organizationRepository, userRepository, registerUserUseCase)

    @Test
    fun `should throw when user attempts to change their own role`() {
        assertThatThrownBy {
            service.updateMemberRole(
                organizationId = 1L,
                targetUserId = 42L,
                role = OrgRole.ADMIN,
                requestingUserId = 42L,
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("own role")
    }

    @Test
    fun `should throw when user attempts to remove themselves from organization`() {
        assertThatThrownBy {
            service.removeMember(
                organizationId = 1L,
                targetUserId = 42L,
                requestingUserId = 42L,
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("remove themselves")
    }
}
