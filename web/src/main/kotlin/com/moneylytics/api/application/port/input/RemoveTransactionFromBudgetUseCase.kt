package com.moneylytics.api.application.port.input

interface RemoveTransactionFromBudgetUseCase {
    fun removeTransactionLink(
        linkId: Long,
        organizationId: Long,
    )
}
