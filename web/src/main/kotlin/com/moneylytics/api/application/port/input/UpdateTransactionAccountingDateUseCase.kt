package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction
import java.time.LocalDate

interface UpdateTransactionAccountingDateUseCase {
    fun updateAccountingDate(
        id: Long,
        organizationId: Long,
        accountingDate: LocalDate,
    ): Transaction?
}
