package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Account
import java.math.BigDecimal
import java.time.LocalDate

interface AccountRepository {
    fun findByIban(
        iban: String,
        organizationId: Long,
    ): Account?

    fun save(
        account: Account,
        organizationId: Long,
    ): Account

    fun findAll(organizationId: Long): List<Account>

    fun delete(
        iban: String,
        organizationId: Long,
    )

    fun updateBalance(
        iban: String,
        organizationId: Long,
        balance: BigDecimal,
        balanceDate: LocalDate,
    )
}
