package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.SplitItem
import com.moneylytics.api.domain.Transaction
import java.math.BigDecimal
import java.time.LocalDate

interface SubTransactionPort {
    fun createVirtualChildren(
        parent: Transaction,
        splits: List<SplitItem>,
        organizationId: Long,
    ): List<Transaction>

    fun createVirtualParent(
        children: List<Transaction>,
        accountingDate: LocalDate,
        name: String?,
        comment: String?,
        organizationId: Long,
    ): Transaction

    fun setExcluded(
        transactionId: Long,
        organizationId: Long,
        excluded: Boolean,
    )

    fun setParentId(
        transactionId: Long,
        organizationId: Long,
        parentId: Long?,
    )

    fun findChildren(
        parentId: Long,
        organizationId: Long,
    ): List<Transaction>

    fun deleteVirtual(
        transactionId: Long,
        organizationId: Long,
    )

    fun updateAmount(
        transactionId: Long,
        organizationId: Long,
        amount: BigDecimal,
    )

    fun createStandaloneVirtual(
        amount: BigDecimal,
        currency: String,
        accountIban: String,
        accountingDate: LocalDate,
        category: String?,
        subcategory: String?,
        counterpartyName: String?,
        purpose: String?,
        organizationId: Long,
    ): Transaction
}
