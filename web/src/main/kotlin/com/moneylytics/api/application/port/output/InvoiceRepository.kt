package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Invoice

interface InvoiceRepository {
    fun findByUserId(userId: Long): List<Invoice>

    fun findByIdAndUserId(
        invoiceId: Long,
        userId: Long,
    ): Invoice?

    fun getPdfData(invoiceId: Long): ByteArray?

    fun save(
        invoice: Invoice,
        pdfData: ByteArray?,
    ): Invoice
}
