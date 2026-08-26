package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.ImportStatus
import com.moneylytics.api.domain.TransactionImport

interface TransactionImportRepository {
    fun save(import: TransactionImport): TransactionImport

    fun findAllByOrganizationId(organizationId: Long): List<TransactionImport>

    fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): TransactionImport?

    fun updateStatus(
        id: Long,
        status: ImportStatus,
    )
}
