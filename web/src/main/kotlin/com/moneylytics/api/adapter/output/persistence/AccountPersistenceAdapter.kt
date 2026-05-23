package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.domain.Account
import org.springframework.stereotype.Component

@Component
class AccountPersistenceAdapter(
    private val jpaRepository: AccountJpaRepository,
) : AccountRepository {
    override fun findByIban(iban: String): Account? = jpaRepository.findByIban(iban)?.toDomain()

    override fun save(account: Account): Account = jpaRepository.save(account.toEntity()).toDomain()

    override fun findAll(): List<Account> = jpaRepository.findAll().map { it.toDomain() }

    private fun Account.toEntity() = AccountEntity(iban = iban, name = name)

    private fun AccountEntity.toDomain() = Account(iban = iban, name = name)
}
