package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.RecurringSyncLog

interface RecurringSyncLogRepository {
    fun save(
        log: RecurringSyncLog,
        userId: Long,
    )

    fun findRecentByUserId(
        userId: Long,
        limit: Int = 20,
    ): List<RecurringSyncLog>
}
