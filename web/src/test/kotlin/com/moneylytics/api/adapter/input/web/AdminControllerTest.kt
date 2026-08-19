package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AdminManageOrgMembersUseCase
import com.moneylytics.api.application.port.input.AssignTierToUserUseCase
import com.moneylytics.api.application.port.input.CreateOrganizationUseCase
import com.moneylytics.api.application.port.input.CreateTierUseCase
import com.moneylytics.api.application.port.input.ListTiersUseCase
import com.moneylytics.api.application.port.input.ListUsersUseCase
import com.moneylytics.api.application.port.input.ListUsersWithOrgsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import com.moneylytics.api.config.ImpersonationWebFilter.Companion.IMPERSONATED_USER_ID_KEY
import com.moneylytics.api.domain.Role
import com.moneylytics.api.domain.Tier
import com.moneylytics.api.domain.User
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebSession
import reactor.core.publisher.Mono

class AdminControllerTest {
    private val syncRecurringSeriesUseCase: SyncRecurringSeriesUseCase = mock()
    private val listUsersUseCase: ListUsersUseCase = mock()
    private val listUsersWithOrgsUseCase: ListUsersWithOrgsUseCase = mock()
    private val createOrganizationUseCase: CreateOrganizationUseCase = mock()
    private val adminManageOrgMembersUseCase: AdminManageOrgMembersUseCase = mock()
    private val resolveUserUseCase: ResolveUserUseCase = mock()
    private val listTiersUseCase: ListTiersUseCase = mock()
    private val createTierUseCase: CreateTierUseCase = mock()
    private val assignTierToUserUseCase: AssignTierToUserUseCase = mock()
    private val controller =
        AdminController(
            syncRecurringSeriesUseCase,
            listUsersUseCase,
            listUsersWithOrgsUseCase,
            createOrganizationUseCase,
            adminManageOrgMembersUseCase,
            resolveUserUseCase,
            listTiersUseCase,
            createTierUseCase,
            assignTierToUserUseCase,
        )

    private val standardTier = Tier(id = 1L, name = "Standard", description = null, active = true, isDefault = true)

    private val sessionAttributes = mutableMapOf<String, Any>()
    private val session: WebSession =
        mock<WebSession>().also {
            whenever(it.attributes).thenReturn(sessionAttributes)
        }
    private val exchange: ServerWebExchange =
        mock<ServerWebExchange>().also {
            whenever(it.session).thenReturn(Mono.just(session))
        }

    @Test
    fun `should call syncForAllOrganizations when recurring sync is triggered`() =
        runTest {
            controller.triggerRecurringSync()
            verify(syncRecurringSeriesUseCase).syncForAllOrganizations()
        }

    @Test
    fun `should set impersonated user id in session when user exists`() =
        runTest {
            val targetUser = User(id = 2L, externalId = "target@test.de", passwordHash = null, role = Role.USER, tier = standardTier)
            whenever(listUsersUseCase.listUsers()).thenReturn(listOf(targetUser))

            controller.impersonate("target@test.de", exchange)

            assertThat(sessionAttributes[IMPERSONATED_USER_ID_KEY]).isEqualTo("target@test.de")
        }

    @Test
    fun `should return 404 when impersonating a non-existent user`() =
        runTest {
            whenever(listUsersUseCase.listUsers()).thenReturn(emptyList())

            val response = runCatching { controller.impersonate("ghost@test.de", exchange) }

            assertThat(response.isFailure).isTrue()
            val ex = response.exceptionOrNull()
            assertThat(ex).isInstanceOf(org.springframework.web.server.ResponseStatusException::class.java)
            assertThat((ex as org.springframework.web.server.ResponseStatusException).statusCode)
                .isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should remove impersonated user id from session on deimpersonate`() =
        runTest {
            sessionAttributes[IMPERSONATED_USER_ID_KEY] = "target@test.de"

            controller.deimpersonate(exchange)

            assertThat(sessionAttributes.containsKey(IMPERSONATED_USER_ID_KEY)).isFalse()
        }

    @Test
    fun `should return list of tiers`() =
        runTest {
            val proTier = Tier(id = 2L, name = "Pro", description = null, active = true, isDefault = false)
            whenever(listTiersUseCase.listTiers()).thenReturn(listOf(standardTier, proTier))

            val result = controller.listTiers()

            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("Standard")
            assertThat(result[1].name).isEqualTo("Pro")
        }

    @Test
    fun `should create a new tier and return it`() =
        runTest {
            val created = Tier(id = 3L, name = "Enterprise", description = "Enterprise features", active = true, isDefault = false)
            whenever(createTierUseCase.createTier("Enterprise", "Enterprise features", false)).thenReturn(created)

            val result = controller.createTier(CreateTierRequest(name = "Enterprise", description = "Enterprise features"))

            assertThat(result.id).isEqualTo(3L)
            assertThat(result.name).isEqualTo("Enterprise")
        }

    @Test
    fun `should assign tier to user`() =
        runTest {
            whenever(resolveUserUseCase.resolveUser("user@test.de")).thenReturn(5L)

            controller.assignTier("user@test.de", AssignTierRequest(tierId = 2L))

            verify(assignTierToUserUseCase).assignTierToUser(5L, 2L)
        }
}
