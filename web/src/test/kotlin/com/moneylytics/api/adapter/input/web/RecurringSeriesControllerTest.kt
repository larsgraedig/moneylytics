package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.ConfirmRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeUseCase
import com.moneylytics.api.application.port.input.CreateRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.DeleteRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.DetectRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetRecurringSyncLogUseCase
import com.moneylytics.api.application.port.input.RequireOrgRoleUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import com.moneylytics.api.domain.OrgRole
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange

class RecurringSeriesControllerTest {
    private val organizationId = 1L
    private val userId = 10L
    private val exchange: ServerWebExchange = mock()
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase = ResolveOrganizationUseCase { _, _ -> organizationId }
    private val resolveUserUseCase: ResolveUserUseCase = ResolveUserUseCase { userId }
    private val syncRecurringSeriesUseCase: SyncRecurringSeriesUseCase = mock()
    private val requireOrgRoleUseCase: RequireOrgRoleUseCase = mock()

    private fun buildController(requireOrgRoleUseCase: RequireOrgRoleUseCase = this.requireOrgRoleUseCase) =
        RecurringSeriesController(
            detectRecurringSeriesUseCase = mock<DetectRecurringSeriesUseCase>(),
            confirmRecurringSeriesUseCase = mock<ConfirmRecurringSeriesUseCase>(),
            getRecurringSeriesUseCase = mock<GetRecurringSeriesUseCase>(),
            correctRecurringSeriesTypeUseCase = mock<CorrectRecurringSeriesTypeUseCase>(),
            createRecurringSeriesUseCase = mock<CreateRecurringSeriesUseCase>(),
            deleteRecurringSeriesUseCase = mock<DeleteRecurringSeriesUseCase>(),
            getRecurringSyncLogUseCase = mock<GetRecurringSyncLogUseCase>(),
            syncRecurringSeriesUseCase = syncRecurringSeriesUseCase,
            resolveOrganizationUseCase = resolveOrganizationUseCase,
            resolveUserUseCase = resolveUserUseCase,
            requireOrgRoleUseCase = requireOrgRoleUseCase,
        )

    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()

    @Test
    fun `should call syncForOrganization when org admin triggers sync`() =
        runTest {
            buildController().syncRecurringSeries(principal, exchange)
            verify(syncRecurringSeriesUseCase).syncForOrganization(organizationId)
        }

    @Test
    fun `should return 403 when a non-admin member triggers sync`() =
        runTest {
            val blockingRequireOrgRole = mock<RequireOrgRoleUseCase>()
            doThrow(IllegalStateException("insufficient role"))
                .`when`(blockingRequireOrgRole)
                .requireOrgRole(organizationId, userId, OrgRole.ADMIN)

            val result = runCatching { buildController(blockingRequireOrgRole).syncRecurringSeries(principal, exchange) }

            assertThat(result.isFailure).isTrue()
            val ex = result.exceptionOrNull()
            assertThat(ex).isInstanceOf(ResponseStatusException::class.java)
            assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
}
