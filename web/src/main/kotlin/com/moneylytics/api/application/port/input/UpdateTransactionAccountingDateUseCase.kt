package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction
import java.time.LocalDate

interface UpdateTransactionAccountingDateUseCase {
    fun updateAccountingDate(
        id: Long,
        userId: Long,
        accountingDate: LocalDate,
    ): Transaction?
}
