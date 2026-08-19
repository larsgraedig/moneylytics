package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetInvoicePdfUseCase
import com.moneylytics.api.application.port.input.ListInvoicesUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.domain.Invoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/invoices")
class InvoiceController(
    private val resolveUserUseCase: ResolveUserUseCase,
    private val listInvoicesUseCase: ListInvoicesUseCase,
    private val getInvoicePdfUseCase: GetInvoicePdfUseCase,
) {
    @GetMapping
    suspend fun listInvoices(
        @AuthenticationPrincipal principal: UserDetails,
    ): List<InvoiceResponse> {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        return withContext(Dispatchers.IO) { listInvoicesUseCase.listInvoices(userId).map { it.toResponse() } }
    }

    @GetMapping("/{id}/pdf")
    suspend fun downloadPdf(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<ByteArrayResource> {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val invoice = withContext(Dispatchers.IO) { listInvoicesUseCase.findInvoice(userId, id) }
        val pdf = withContext(Dispatchers.IO) { getInvoicePdfUseCase.getInvoicePdf(userId, id) }
        val filename = invoice?.invoiceNumber?.let { "$it.pdf" } ?: "invoice-$id.pdf"
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(ByteArrayResource(pdf))
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

data class InvoiceResponse(
    val id: Long,
    val invoiceNumber: String?,
    val amountCents: Int,
    val currency: String,
    val status: String,
    val periodStart: String,
    val periodEnd: String,
    val hasPdf: Boolean,
    val issuedAt: String,
)

private fun Invoice.toResponse() =
    InvoiceResponse(
        id = id,
        invoiceNumber = invoiceNumber,
        amountCents = amountCents,
        currency = currency,
        status = status,
        periodStart = periodStart.format(dateFormatter),
        periodEnd = periodEnd.format(dateFormatter),
        hasPdf = hasPdf,
        issuedAt = issuedAt.format(dateFormatter),
    )
