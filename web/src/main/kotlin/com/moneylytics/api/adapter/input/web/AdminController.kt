package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController(
    private val syncRecurringSeriesUseCase: SyncRecurringSeriesUseCase,
) {
    @PostMapping("/recurring/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun triggerRecurringSync() =
        withContext(Dispatchers.IO) {
            syncRecurringSeriesUseCase.syncForAllUsers()
        }
}
