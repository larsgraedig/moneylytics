package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

interface AcceptSuggestionUseCase {
    fun acceptSuggestion(
        id: Long,
        organizationId: Long,
    ): Transaction?
}
