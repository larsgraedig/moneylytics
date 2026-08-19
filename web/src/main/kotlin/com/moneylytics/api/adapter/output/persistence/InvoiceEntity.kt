package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "invoice")
class InvoiceEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "stripe_invoice_id", nullable = true, unique = true, length = 255)
    val stripeInvoiceId: String? = null,
    @Column(name = "amount_cents", nullable = false)
    val amountCents: Int,
    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,
    @Column(name = "status", nullable = false, length = 50)
    var status: String,
    @Column(name = "period_start", nullable = false)
    val periodStart: LocalDateTime,
    @Column(name = "period_end", nullable = false)
    val periodEnd: LocalDateTime,
    @Column(name = "invoice_number", nullable = true, length = 100)
    val invoiceNumber: String? = null,
    @Column(name = "pdf_data", nullable = true)
    var pdfData: ByteArray? = null,
    @Column(name = "issued_at", nullable = false)
    val issuedAt: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
