package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Account

fun interface GetAccountsUseCase {
    fun getAccounts(organizationId: Long): List<Account>
}
