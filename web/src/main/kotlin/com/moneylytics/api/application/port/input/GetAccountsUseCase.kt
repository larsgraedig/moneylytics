package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Account

fun interface GetAccountsUseCase {
    fun getAccounts(userId: Long): List<Account>
}
