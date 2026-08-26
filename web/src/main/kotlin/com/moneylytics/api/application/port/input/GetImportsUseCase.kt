package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.TransactionImport

fun interface GetImportsUseCase {
    fun getImports(organizationId: Long): List<TransactionImport>
}
