package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Account
import java.math.BigDecimal
import java.time.LocalDate

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

    fun updateBalance(
        iban: String,
        userId: Long,
        balance: BigDecimal,
        balanceDate: LocalDate,
    )
}
