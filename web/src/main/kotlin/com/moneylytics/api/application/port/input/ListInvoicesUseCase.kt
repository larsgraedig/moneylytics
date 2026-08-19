package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Invoice

interface ListInvoicesUseCase {
    fun listInvoices(userId: Long): List<Invoice>

    fun findInvoice(
        userId: Long,
        invoiceId: Long,
    ): Invoice?
}
