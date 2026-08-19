package com.moneylytics.api.application.port.input

interface GetInvoicePdfUseCase {
    fun getInvoicePdf(
        userId: Long,
        invoiceId: Long,
    ): ByteArray
}
