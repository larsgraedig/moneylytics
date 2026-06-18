package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.domain.Account
import org.springframework.stereotype.Component

@Component
class AccountPersistenceAdapter(
    private val jpaRepository: AccountJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : AccountRepository {
    override fun findByIban(
        iban: String,
        userId: Long,
    ): Account? = jpaRepository.findByIbanAndUserId(iban, userId)?.toDomain()

    override fun save(
        account: Account,
        userId: Long,
    ): Account = jpaRepository.save(account.toEntity(userId)).toDomain()

    override fun findAll(userId: Long): List<Account> = jpaRepository.findAllByUserId(userId).map { it.toDomain() }

    override fun delete(
        iban: String,
        userId: Long,
    ) = jpaRepository.deleteByIbanAndUserId(iban, userId)

    private fun Account.toEntity(userId: Long) =
        AccountEntity(
            iban = iban,
            name = name,
            user = userJpaRepository.getReferenceById(userId),
        )

    private fun AccountEntity.toDomain() = Account(iban = iban, name = name)
}
