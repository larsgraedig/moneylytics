package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Account

interface AccountRepository {
    fun findByIban(iban: String): Account?

    fun save(account: Account): Account

    fun findAll(): List<Account>
}
