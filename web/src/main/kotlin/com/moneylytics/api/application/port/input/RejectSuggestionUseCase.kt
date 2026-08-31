package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

fun interface RejectSuggestionUseCase {
    fun reject(
        transactionId: Long,
        organizationId: Long,
    ): Transaction?
}
