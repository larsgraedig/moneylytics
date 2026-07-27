package com.moneylytics.api.application.port.input

interface SyncRecurringSeriesUseCase {
    fun syncForAllOrganizations()

    fun syncForOrganization(organizationId: Long)
}
