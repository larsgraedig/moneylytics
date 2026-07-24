package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeCommand
import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeUseCase
import com.moneylytics.api.application.port.input.DetectRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetRecurringSeriesQuery
import com.moneylytics.api.application.port.input.GetRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurringType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
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

@RestController
@RequestMapping("/transactions")
class RecurringSeriesController(
    private val detectRecurringSeriesUseCase: DetectRecurringSeriesUseCase,
    private val getRecurringSeriesUseCase: GetRecurringSeriesUseCase,
    private val correctRecurringSeriesTypeUseCase: CorrectRecurringSeriesTypeUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @GetMapping("/recurring")
    suspend fun getRecurringSeries(
        @RequestParam(required = false) direction: RecurrenceDirection? = null,
        @RequestParam(required = false) type: RecurringType? = null,
        @AuthenticationPrincipal principal: UserDetails,
    ): List<RecurringSeriesItem> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            getRecurringSeriesUseCase
                .getRecurringSeries(GetRecurringSeriesQuery(userId = userId, direction = direction, type = type))
                .map { it.toItem() }
        }

    @PostMapping("/recurring/refresh")
    suspend fun refreshRecurringSeries(
        @AuthenticationPrincipal principal: UserDetails,
    ): List<RecurringSeriesItem> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            detectRecurringSeriesUseCase
                .detect(RefreshRecurringSeriesCommand(userId = userId))
                .map { it.toItem() }
        }

    @PatchMapping("/recurring/{id}/type")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun correctType(
        @PathVariable id: Long,
        @RequestBody body: CorrectTypeRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ) = withContext(Dispatchers.IO) {
        val userId = resolveUserUseCase.resolveUser(principal.username)
        try {
            correctRecurringSeriesTypeUseCase.correctType(
                CorrectRecurringSeriesTypeCommand(seriesId = id, userId = userId, type = body.type),
            )
        } catch (e: NoSuchElementException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        }
    }
}

data class CorrectTypeRequest(
    val type: RecurringType,
)
