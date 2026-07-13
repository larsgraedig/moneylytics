package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.DetectRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetRecurringSeriesQuery
import com.moneylytics.api.application.port.input.GetRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurringType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/transactions")
class RecurringSeriesController(
    private val detectRecurringSeriesUseCase: DetectRecurringSeriesUseCase,
    private val getRecurringSeriesUseCase: GetRecurringSeriesUseCase,
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
}
