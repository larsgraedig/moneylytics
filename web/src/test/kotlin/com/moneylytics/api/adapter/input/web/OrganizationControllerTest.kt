package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.ActivateOrganizationUseCase
import com.moneylytics.api.application.port.input.GetOrganizationsUseCase
import com.moneylytics.api.application.port.input.ManageOrganizationMembersUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.service.UserAlreadyExistsException
import com.moneylytics.api.domain.OrgRole
import com.moneylytics.api.domain.Organization
import com.moneylytics.api.domain.OrganizationMembership
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.User
import org.springframework.web.server.ResponseStatusException

class OrganizationControllerTest {
    private val userId = 1L
    private val orgId = 6L
    private val resolveUserUseCase: ResolveUserUseCase = ResolveUserUseCase { userId }
    private val getOrganizationsUseCase: GetOrganizationsUseCase = mock()
    private val manageOrganizationMembersUseCase: ManageOrganizationMembersUseCase = mock()
    private val activateOrganizationUseCase: ActivateOrganizationUseCase = mock()
    private val controller =
        OrganizationController(
            getOrganizationsUseCase = getOrganizationsUseCase,
            manageOrganizationMembersUseCase = manageOrganizationMembersUseCase,
            activateOrganizationUseCase = activateOrganizationUseCase,
            resolveUserUseCase = resolveUserUseCase,
        )
    private val principal =
        User
            .withUsername("admin@test.de")
            .password("x")
            .roles("USER")
            .build()

    @Test
    fun `should create user and add as member when addMember is called`() =
        runTest {
            val request = AddMemberRequest(email = "new@test.de", password = "secret123", role = "MEMBER")
            val captor = argumentCaptor<String>()

            controller.addMember(id = orgId, request = request, principal = principal)

            verify(manageOrganizationMembersUseCase).addMember(
                organizationId = any(),
                email = captor.capture(),
                password = any(),
                role = any(),
                requestingUserId = any(),
            )
            assertThat(captor.firstValue).isEqualTo("new@test.de")
        }

    @Test
    fun `should return 409 when addMember is called with an already existing email`() =
        runTest {
            doThrow(UserAlreadyExistsException("new@test.de"))
                .whenever(manageOrganizationMembersUseCase)
                .addMember(any(), any(), any(), any(), any())
            val request = AddMemberRequest(email = "new@test.de", password = "secret123", role = "MEMBER")

            val ex =
                assertThrows<ResponseStatusException> {
                    controller.addMember(id = orgId, request = request, principal = principal)
                }

            assertThat(ex.statusCode.value()).isEqualTo(409)
        }

    @Test
    fun `should return members of the organization`() =
        runTest {
            whenever(manageOrganizationMembersUseCase.getMembers(orgId)).thenReturn(
                listOf(
                    OrganizationMembership(
                        userId = 42L,
                        email = "member@test.de",
                        role = OrgRole.MEMBER,
                        organization = Organization(id = 6L, name = "Test Org"),
                    ),
                ),
            )

            val result = controller.getMembers(id = orgId)

            assertThat(result).hasSize(1)
            assertThat(result.first().userId).isEqualTo(42L)
            assertThat(result.first().role).isEqualTo("MEMBER")
        }
}
