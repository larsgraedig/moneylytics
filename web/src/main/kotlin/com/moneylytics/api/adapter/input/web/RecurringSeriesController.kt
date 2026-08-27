package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.ConfirmRecurringSeriesCommand
import com.moneylytics.api.application.port.input.ConfirmRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeCommand
import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeUseCase
import com.moneylytics.api.application.port.input.CreateRecurringSeriesCommand
import com.moneylytics.api.application.port.input.CreateRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.DeleteRecurringSeriesCommand
import com.moneylytics.api.application.port.input.DeleteRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.DetectRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetRecurringSeriesQuery
import com.moneylytics.api.application.port.input.GetRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetRecurringSyncLogUseCase
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.application.port.input.RequireOrgRoleUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import com.moneylytics.api.domain.OrgRole
import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurringType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/transactions")
class RecurringSeriesController(
    private val detectRecurringSeriesUseCase: DetectRecurringSeriesUseCase,
    private val confirmRecurringSeriesUseCase: ConfirmRecurringSeriesUseCase,
    private val getRecurringSeriesUseCase: GetRecurringSeriesUseCase,
    private val correctRecurringSeriesTypeUseCase: CorrectRecurringSeriesTypeUseCase,
    private val createRecurringSeriesUseCase: CreateRecurringSeriesUseCase,
    private val deleteRecurringSeriesUseCase: DeleteRecurringSeriesUseCase,
    private val getRecurringSyncLogUseCase: GetRecurringSyncLogUseCase,
    private val syncRecurringSeriesUseCase: SyncRecurringSeriesUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
    private val requireOrgRoleUseCase: RequireOrgRoleUseCase,
) {
    @GetMapping("/recurring")
    suspend fun getRecurringSeries(
        @RequestParam(required = false) direction: RecurrenceDirection? = null,
        @RequestParam(required = false) type: RecurringType? = null,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): List<RecurringSeriesItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            getRecurringSeriesUseCase
                .getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId, direction = direction, type = type))
                .map { it.toItem() }
        }
    }

    @PostMapping("/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createRecurringSeries(
        @RequestBody body: CreateRecurringSeriesRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): RecurringSeriesItem {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            createRecurringSeriesUseCase
                .create(
                    CreateRecurringSeriesCommand(
                        organizationId = organizationId,
                        label = body.label,
                        type = body.type,
                        direction = body.direction,
                        cadence = body.cadence,
                        expectedAmount = body.expectedAmount,
                        currency = body.currency,
                        accountIban = body.accountIban,
                        lastBookingDate = body.lastBookingDate,
                    ),
                ).toItem()
        }
    }

    @DeleteMapping("/recurring/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteRecurringSeries(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ) {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            try {
                deleteRecurringSeriesUseCase.delete(DeleteRecurringSeriesCommand(organizationId = organizationId, seriesId = id))
            } catch (e: NoSuchElementException) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message, e)
            }
        }
    }

    @PostMapping("/recurring/refresh")
    suspend fun refreshRecurringSeries(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): List<RecurringSeriesItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            detectRecurringSeriesUseCase
                .detect(RefreshRecurringSeriesCommand(organizationId = organizationId))
                .map { it.toItem() }
        }
    }

    @PostMapping("/recurring/confirm")
    suspend fun confirmRecurringSeries(
        @RequestBody body: ConfirmRecurringSeriesRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): List<RecurringSeriesItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            confirmRecurringSeriesUseCase
                .confirm(
                    ConfirmRecurringSeriesCommand(
                        organizationId = organizationId,
                        confirmedFingerprints = body.confirmedFingerprints,
                        falsePositiveFingerprints = body.falsePositiveFingerprints,
                    ),
                ).map { it.toItem() }
        }
    }

    @GetMapping("/recurring/sync-log")
    suspend fun getSyncLog(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): List<RecurringSyncLogItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            getRecurringSyncLogUseCase.getRecentSyncLogs(organizationId).map { it.toItem() }
        }
    }

    @PostMapping("/recurring/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun syncRecurringSeries(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ) {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            try {
                requireOrgRoleUseCase.requireOrgRole(organizationId, userId, OrgRole.ADMIN)
            } catch (e: IllegalStateException) {
                logger.warn { "Forbidden recurring series sync: ${e.message}" }
                throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message, e)
            }
            syncRecurringSeriesUseCase.syncForOrganization(organizationId)
        }
    }

    @PatchMapping("/recurring/{id}/type")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun correctType(
        @PathVariable id: Long,
        @RequestBody body: CorrectTypeRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ) {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            try {
                correctRecurringSeriesTypeUseCase.correctType(
                    CorrectRecurringSeriesTypeCommand(seriesId = id, organizationId = organizationId, type = body.type),
                )
            } catch (e: NoSuchElementException) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message, e)
            }
        }
    }
}

data class CorrectTypeRequest(
    val type: RecurringType,
)

data class ConfirmRecurringSeriesRequest(
    val confirmedFingerprints: List<String>,
    val falsePositiveFingerprints: List<String>,
)

data class CreateRecurringSeriesRequest(
    val label: String,
    val type: RecurringType,
    val direction: RecurrenceDirection,
    val cadence: RecurrenceCadence,
    val expectedAmount: BigDecimal,
    val currency: String = "EUR",
    val accountIban: String = "",
    val lastBookingDate: LocalDate? = null,
)
