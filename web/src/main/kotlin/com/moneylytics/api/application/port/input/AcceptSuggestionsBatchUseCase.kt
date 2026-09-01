package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

interface AcceptSuggestionsBatchUseCase {
    fun acceptSuggestions(
        ids: List<Long>,
        organizationId: Long,
    ): List<Transaction>

    fun rejectSuggestions(
        ids: List<Long>,
        organizationId: Long,
    )
}
