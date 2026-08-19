package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.InvoiceRepository
import com.moneylytics.api.domain.Invoice
import org.springframework.stereotype.Component

internal fun InvoiceEntity.toDomain() =
    Invoice(
        id = id!!,
        userId = userId,
        stripeInvoiceId = stripeInvoiceId,
        amountCents = amountCents,
        currency = currency,
        status = status,
        periodStart = periodStart,
        periodEnd = periodEnd,
        hasPdf = pdfData != null,
        issuedAt = issuedAt,
    )

@Component
class InvoicePersistenceAdapter(
    private val jpaRepository: InvoiceJpaRepository,
) : InvoiceRepository {
    override fun findByUserId(userId: Long): List<Invoice> = jpaRepository.findByUserIdOrderByIssuedAtDesc(userId).map { it.toDomain() }

    override fun findByIdAndUserId(
        invoiceId: Long,
        userId: Long,
    ): Invoice? = jpaRepository.findByIdAndUserId(invoiceId, userId)?.toDomain()

    override fun getPdfData(invoiceId: Long): ByteArray? = jpaRepository.findById(invoiceId).orElse(null)?.pdfData

    override fun save(
        invoice: Invoice,
        pdfData: ByteArray?,
    ): Invoice {
        val entity =
            InvoiceEntity(
                userId = invoice.userId,
                stripeInvoiceId = invoice.stripeInvoiceId,
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
