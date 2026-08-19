package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetInvoicePdfUseCase
import com.moneylytics.api.application.port.input.ListInvoicesUseCase
import com.moneylytics.api.application.port.output.InvoiceRepository
import com.moneylytics.api.domain.Invoice
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
) : ListInvoicesUseCase,
    GetInvoicePdfUseCase {
    override fun listInvoices(userId: Long): List<Invoice> = invoiceRepository.findByUserId(userId)

    override fun getInvoicePdf(
        userId: Long,
        invoiceId: Long,
    ): ByteArray {
        invoiceRepository.findByIdAndUserId(invoiceId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found")
        return invoiceRepository.getPdfData(invoiceId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not available")
    }
}
