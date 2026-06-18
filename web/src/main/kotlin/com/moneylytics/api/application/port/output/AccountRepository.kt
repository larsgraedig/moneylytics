package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Account

interface AccountRepository {
    fun findByIban(
        iban: String,
        userId: Long,
    ): Account?

    fun save(
        account: Account,
        userId: Long,
    ): Account

    fun findAll(userId: Long): List<Account>

    fun delete(
        iban: String,
        userId: Long,
    )
}
