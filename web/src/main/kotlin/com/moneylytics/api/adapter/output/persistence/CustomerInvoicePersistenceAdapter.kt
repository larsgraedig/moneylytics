package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.InvoiceRepository
import com.moneylytics.api.domain.Invoice
import org.springframework.stereotype.Component

internal fun CustomerInvoiceEntity.toDomain() =
    Invoice(
        id = id!!,
        userId = userId,
        stripeInvoiceId = stripeInvoiceId,
        invoiceNumber = invoiceNumber,
        amountCents = amountCents,
        currency = currency,
        status = status,
        periodStart = periodStart,
        periodEnd = periodEnd,
        hasPdf = pdfData != null || stripeInvoiceId != null,
        issuedAt = issuedAt,
    )

@Component
class CustomerInvoicePersistenceAdapter(
    private val jpaRepository: CustomerInvoiceJpaRepository,
) : InvoiceRepository {
    override fun findByUserId(userId: Long): List<Invoice> = jpaRepository.findByUserIdOrderByIssuedAtDesc(userId).map { it.toDomain() }

    override fun findByIdAndUserId(
        invoiceId: Long,
        userId: Long,
    ): Invoice? = jpaRepository.findByIdAndUserId(invoiceId, userId)?.toDomain()

    override fun getPdfData(invoiceId: Long): ByteArray? = jpaRepository.findById(invoiceId).orElse(null)?.pdfData

    override fun findByStripeInvoiceId(stripeInvoiceId: String): Invoice? = jpaRepository.findByStripeInvoiceId(stripeInvoiceId)?.toDomain()

    override fun updateStatus(
        invoiceId: Long,
        status: String,
    ) {
        val entity = jpaRepository.findById(invoiceId).orElse(null) ?: return
        entity.status = status
        jpaRepository.save(entity)
    }

    override fun updateInvoiceNumber(
        invoiceId: Long,
        number: String,
    ) {
        val entity = jpaRepository.findById(invoiceId).orElse(null) ?: return
        entity.invoiceNumber = number
        jpaRepository.save(entity)
    }

    override fun save(
        invoice: Invoice,
        pdfData: ByteArray?,
    ): Invoice {
        val entity =
            CustomerInvoiceEntity(
                userId = invoice.userId,
                stripeInvoiceId = invoice.stripeInvoiceId,
                invoiceNumber = invoice.invoiceNumber,
                amountCents = invoice.amountCents,
                currency = invoice.currency,
                status = invoice.status,
                periodStart = invoice.periodStart,
                periodEnd = invoice.periodEnd,
                pdfData = pdfData,
                issuedAt = invoice.issuedAt,
            )
        return jpaRepository.save(entity).toDomain()
    }
}
