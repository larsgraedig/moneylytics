package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.RecurringSyncLog

interface RecurringSyncLogRepository {
    fun save(
        log: RecurringSyncLog,
        organizationId: Long,
    )

    fun findRecentByOrganizationId(
        organizationId: Long,
        limit: Int = 20,
    ): List<RecurringSyncLog>
}
