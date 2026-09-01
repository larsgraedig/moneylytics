package com.moneylytics.api.domain

data class TransactionsImportedEvent(
    val organizationId: Long,
    val importId: Long,
    val importedIds: List<Long>,
)
