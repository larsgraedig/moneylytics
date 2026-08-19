package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.InvoiceRepository
import com.moneylytics.api.application.port.output.StripeGateway
import com.moneylytics.api.domain.Invoice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

class InvoiceServiceTest {
    private val invoiceRepository: InvoiceRepository = mock()
    private val stripeGateway: StripeGateway = mock()
    private val service = InvoiceService(invoiceRepository, stripeGateway)

    private val userId = 1L
    private val invoiceId = 42L
    private val now = LocalDateTime.now()

    private val invoice =
        Invoice(
            id = invoiceId,
            userId = userId,
            stripeInvoiceId = "in_test",
            amountCents = 999,
            currency = "eur",
            status = "paid",
            periodStart = now.minusMonths(1),
            periodEnd = now,
            hasPdf = true,
            issuedAt = now,
        )

    @Test
    fun `should return invoices for user ordered by issued date`() {
        whenever(invoiceRepository.findByUserId(userId)).thenReturn(listOf(invoice))

        val result = service.listInvoices(userId)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(invoiceId)
    }

    @Test
    fun `should return empty list when user has no invoices`() {
        whenever(invoiceRepository.findByUserId(userId)).thenReturn(emptyList())

        val result = service.listInvoices(userId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should return pdf bytes when invoice exists and has pdf`() {
        val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        whenever(invoiceRepository.findByIdAndUserId(invoiceId, userId)).thenReturn(invoice)
        whenever(invoiceRepository.getPdfData(invoiceId)).thenReturn(pdfBytes)

        val result = service.getInvoicePdf(userId, invoiceId)

        assertThat(result).isEqualTo(pdfBytes)
    }

    @Test
    fun `should throw NOT_FOUND when invoice does not belong to user`() {
        whenever(invoiceRepository.findByIdAndUserId(invoiceId, userId)).thenReturn(null)

        val ex =
            assertThrows<ResponseStatusException> {
                service.getInvoicePdf(userId, invoiceId)
            }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `should fallback to Stripe API when pdf data is missing from DB`() {
        val stripePdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        whenever(invoiceRepository.findByIdAndUserId(invoiceId, userId)).thenReturn(invoice)
        whenever(invoiceRepository.getPdfData(invoiceId)).thenReturn(null)
        whenever(stripeGateway.downloadInvoicePdfById("in_test")).thenReturn(stripePdfBytes)

        val result = service.getInvoicePdf(userId, invoiceId)

        assertThat(result).isEqualTo(stripePdfBytes)
    }

    @Test
    fun `should throw NOT_FOUND when pdf data is missing and Stripe API fails`() {
        whenever(invoiceRepository.findByIdAndUserId(invoiceId, userId)).thenReturn(invoice)
        whenever(invoiceRepository.getPdfData(invoiceId)).thenReturn(null)
        whenever(stripeGateway.downloadInvoicePdfById("in_test")).thenThrow(RuntimeException("Stripe error"))

        val ex =
            assertThrows<ResponseStatusException> {
                service.getInvoicePdf(userId, invoiceId)
            }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `should throw NOT_FOUND when pdf data is missing and invoice has no stripe id`() {
        val invoiceWithoutStripeId = invoice.copy(stripeInvoiceId = null)
        whenever(invoiceRepository.findByIdAndUserId(invoiceId, userId)).thenReturn(invoiceWithoutStripeId)
        whenever(invoiceRepository.getPdfData(invoiceId)).thenReturn(null)

        val ex =
            assertThrows<ResponseStatusException> {
                service.getInvoicePdf(userId, invoiceId)
            }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
