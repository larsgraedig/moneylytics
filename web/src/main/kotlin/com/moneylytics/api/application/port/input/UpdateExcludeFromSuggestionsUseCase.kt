package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

interface UpdateExcludeFromSuggestionsUseCase {
    fun updateExcludeFromSuggestions(
        id: Long,
        organizationId: Long,
        excludeFromSuggestions: Boolean,
    ): Transaction?
}
