package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AdminControllerTest {
    private val syncRecurringSeriesUseCase: SyncRecurringSeriesUseCase = mock()
    private val controller = AdminController(syncRecurringSeriesUseCase)

    @Test
    fun `should call syncForAllUsers when recurring sync is triggered`() =
        runTest {
            controller.triggerRecurringSync()

            verify(syncRecurringSeriesUseCase).syncForAllUsers()
        }
}
