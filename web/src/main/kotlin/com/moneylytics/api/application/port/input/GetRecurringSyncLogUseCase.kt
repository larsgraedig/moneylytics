package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.RecurringSyncLog

interface GetRecurringSyncLogUseCase {
    fun getRecentSyncLogs(organizationId: Long): List<RecurringSyncLog>
}
