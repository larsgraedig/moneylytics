package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.domain.Transaction
import java.math.BigDecimal

data class TransactionListResponse(
    val transactions: List<TransactionItem>,
    val total: BigDecimal,
)

data class TransactionItem(
    val id: Long,
    val bookingDate: String,
    val accountingDate: String,
    val accountIban: String,
    val category: String?,
    val subcategory: String?,
    val categoryGroup: String?,
    val amount: BigDecimal,
    val effectiveAmount: BigDecimal,
    val currency: String,
    val offsetLinks: List<OffsetLinkItem>,
    val comment: String?,
    val purpose: String?,
    val counterpartyName: String?,
    val counterpartyIban: String?,
)

data class OffsetLinkItem(
    val id: Long,
    val linkedTransactionId: Long,
    val linkedTransactionAmount: BigDecimal,
    val partialAmount: BigDecimal?,
)

fun Transaction.toItem() =
    TransactionItem(
        id = requireNotNull(id),
        bookingDate = bookingDate.toString(),
        accountingDate = accountingDate.toString(),
        accountIban = accountIban,
        category = category,
        subcategory = subcategory,
        categoryGroup = categoryGroup,
        amount = amount,
        effectiveAmount = effectiveAmount(),
        currency = currency,
        offsetLinks =
            offsetLinks.map { link ->
                OffsetLinkItem(
                    id = link.id,
                    linkedTransactionId = link.linkedTransactionId,
                    linkedTransactionAmount = link.linkedTransactionAmount,
                    partialAmount = link.partialAmount,
                )
            },
        comment = comment,
        purpose = purpose,
        counterpartyName = counterpartyName,
        counterpartyIban = counterpartyIban,
    )
