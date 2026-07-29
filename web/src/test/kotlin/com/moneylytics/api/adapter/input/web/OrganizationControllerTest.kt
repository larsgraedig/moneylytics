package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.ActivateOrganizationUseCase
import com.moneylytics.api.application.port.input.CreateInvitationUseCase
import com.moneylytics.api.application.port.input.GetOrganizationsUseCase
import com.moneylytics.api.application.port.input.ListPendingInvitationsUseCase
import com.moneylytics.api.application.port.input.ManageOrganizationMembersUseCase
import com.moneylytics.api.application.port.input.OnboardOrganizationUseCase
import com.moneylytics.api.application.port.input.OrganizationLogoUseCase
import com.moneylytics.api.application.port.input.RequireOrgRoleUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.domain.OrgRole
import com.moneylytics.api.domain.Organization
import com.moneylytics.api.domain.OrganizationMembership
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.User

class OrganizationControllerTest {
    private val userId = 1L
    private val orgId = 6L
    private val resolveUserUseCase: ResolveUserUseCase = ResolveUserUseCase { userId }
    private val getOrganizationsUseCase: GetOrganizationsUseCase = mock()
    private val manageOrganizationMembersUseCase: ManageOrganizationMembersUseCase = mock()
    private val activateOrganizationUseCase: ActivateOrganizationUseCase = mock()
    private val requireOrgRoleUseCase: RequireOrgRoleUseCase = mock()
    private val createInvitationUseCase: CreateInvitationUseCase = mock()
    private val listPendingInvitationsUseCase: ListPendingInvitationsUseCase = mock()
    private val onboardOrganizationUseCase: OnboardOrganizationUseCase = mock()
    private val organizationLogoUseCase: OrganizationLogoUseCase = mock()
    private val controller =
        OrganizationController(
            getOrganizationsUseCase = getOrganizationsUseCase,
            manageOrganizationMembersUseCase = manageOrganizationMembersUseCase,
            activateOrganizationUseCase = activateOrganizationUseCase,
            requireOrgRoleUseCase = requireOrgRoleUseCase,
            createInvitationUseCase = createInvitationUseCase,
            listPendingInvitationsUseCase = listPendingInvitationsUseCase,
            onboardOrganizationUseCase = onboardOrganizationUseCase,
            organizationLogoUseCase = organizationLogoUseCase,
            resolveUserUseCase = resolveUserUseCase,
        )
    private val principal =
        User
            .withUsername("admin@test.de")
            .password("x")
            .roles("USER")
            .build()

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
