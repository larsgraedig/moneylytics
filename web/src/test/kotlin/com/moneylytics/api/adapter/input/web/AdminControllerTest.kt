package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AdminManageOrgMembersUseCase
import com.moneylytics.api.application.port.input.CreateOrganizationUseCase
import com.moneylytics.api.application.port.input.ListUsersUseCase
import com.moneylytics.api.application.port.input.ListUsersWithOrgsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import com.moneylytics.api.config.ImpersonationWebFilter.Companion.IMPERSONATED_USER_ID_KEY
import com.moneylytics.api.domain.Role
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
    private val controller =
        AdminController(
            syncRecurringSeriesUseCase,
            listUsersUseCase,
            listUsersWithOrgsUseCase,
            createOrganizationUseCase,
            adminManageOrgMembersUseCase,
            resolveUserUseCase,
        )

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
            val targetUser = User(id = 2L, externalId = "target@test.de", passwordHash = null, role = Role.USER)
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
}
