package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetInvoicePdfUseCase
import com.moneylytics.api.application.port.input.ListInvoicesUseCase
import com.moneylytics.api.application.port.output.InvoiceRepository
import com.moneylytics.api.application.port.output.StripeGateway
import com.moneylytics.api.domain.Invoice
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val stripeGateway: StripeGateway,
) : ListInvoicesUseCase,
    GetInvoicePdfUseCase {
    override fun listInvoices(userId: Long): List<Invoice> = invoiceRepository.findByUserId(userId)

    override fun findInvoice(
        userId: Long,
        invoiceId: Long,
    ): Invoice? = invoiceRepository.findByIdAndUserId(invoiceId, userId)

    override fun getInvoicePdf(
        userId: Long,
        invoiceId: Long,
    ): ByteArray {
        val invoice =
            invoiceRepository.findByIdAndUserId(invoiceId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found")

        invoiceRepository.getPdfData(invoiceId)?.let { return it }

        val stripeInvoiceId =
            invoice.stripeInvoiceId
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not available")
        return runCatching { stripeGateway.downloadInvoicePdfById(stripeInvoiceId) }
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not available") }
    }
}
