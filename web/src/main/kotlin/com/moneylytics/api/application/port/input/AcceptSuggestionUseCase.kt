package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

fun interface AcceptSuggestionUseCase {
    fun accept(
        transactionId: Long,
        organizationId: Long,
    ): Transaction?
}
