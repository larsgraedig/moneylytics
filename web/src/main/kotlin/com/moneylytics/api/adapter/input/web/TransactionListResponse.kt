package com.moneylytics.api.adapter.input.web

import com.fasterxml.jackson.annotation.JsonProperty
import com.moneylytics.api.domain.Transaction
import java.math.BigDecimal

data class BudgetLinkSummaryDto(
    val linkId: Long,
    val budgetId: Long,
    val budgetName: String,
    val amount: BigDecimal?,
)

data class TransactionListResponse(
    val transactions: List<TransactionItem>,
    val total: BigDecimal,
    val totalCount: Int,
    val hasMore: Boolean,
)

data class TransactionItem(
    val id: Long,
    val bookingDate: String,
    val accountingDate: String,
    val accountIban: String,
    val categoryId: Long?,
    val suggestedCategoryId: Long?,
    val category: String?,
    val subcategory: String?,
    val group: String?,
    val amount: BigDecimal,
    val effectiveAmount: BigDecimal,
    val currency: String,
    val offsetLinks: List<OffsetLinkItem>,
    val groups: List<GroupSummaryDto>,
    val collections: List<CollectionSummaryDto>,
    val budgetLinks: List<BudgetLinkSummaryDto>,
    val comment: String?,
    val purpose: String?,
    val counterpartyName: String?,
    val counterpartyIban: String?,
    val parentId: Long?,
    @get:JsonProperty("isVirtual")
    val isVirtual: Boolean,
    val excluded: Boolean,
    val excludeFromSuggestions: Boolean,
)

data class GroupSummaryDto(
    val id: Long,
    val name: String?,
)

data class CollectionSummaryDto(
    val id: Long,
    val name: String,
)

data class OffsetLinkItem(
    val id: Long,
    val linkedTransactionId: Long,
    val linkedTransactionAmount: BigDecimal,
    val amountA: BigDecimal?,
    val amountB: BigDecimal?,
    val committedAmount: BigDecimal,
    val comment: String?,
    val groupId: Long?,
)

fun Transaction.toItem() =
    TransactionItem(
        id = requireNotNull(id),
        bookingDate = bookingDate.toString(),
        accountingDate = accountingDate.toString(),
        accountIban = accountIban,
        categoryId = categoryId,
        suggestedCategoryId = suggestedCategoryId,
        category = category,
        subcategory = subcategory,
        group = group,
        amount = amount,
        effectiveAmount = effectiveAmount(),
        currency = currency,
        offsetLinks =
            offsetLinks.map { link ->
                OffsetLinkItem(
                    id = link.id,
                    linkedTransactionId = link.linkedTransactionId,
                    linkedTransactionAmount = link.linkedTransactionAmount,
                    amountA = link.amountA,
                    amountB = link.amountB,
                    committedAmount = link.myCommitted,
                    comment = link.comment,
                    groupId = link.groupId,
                )
            },
        groups = groups.map { GroupSummaryDto(it.id, it.name) },
        collections = collections.map { CollectionSummaryDto(it.id, it.name) },
        budgetLinks = budgetLinks.map { BudgetLinkSummaryDto(it.linkId, it.budgetId, it.budgetName, it.amount) },
        comment = comment,
        purpose = purpose,
        counterpartyName = counterpartyName,
        counterpartyIban = counterpartyIban,
        parentId = parentId,
        isVirtual = isVirtual,
        excluded = excluded,
        excludeFromSuggestions = excludeFromSuggestions,
    )
