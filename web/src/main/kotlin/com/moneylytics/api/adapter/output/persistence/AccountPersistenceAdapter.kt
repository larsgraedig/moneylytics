package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.domain.Account
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class AccountPersistenceAdapter(
    private val jpaRepository: AccountJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
) : AccountRepository {
    override fun findByIban(
        iban: String,
        organizationId: Long,
    ): Account? = jpaRepository.findByIbanAndOrganizationId(iban, organizationId)?.toDomain()

    override fun save(
        account: Account,
        organizationId: Long,
    ): Account = jpaRepository.save(account.toEntity(organizationId)).toDomain()

    override fun findAll(organizationId: Long): List<Account> = jpaRepository.findAllByOrganizationId(organizationId).map { it.toDomain() }

    override fun delete(
        iban: String,
        organizationId: Long,
    ) = jpaRepository.deleteByIbanAndOrganizationId(iban, organizationId)

    override fun updateBalance(
        iban: String,
        organizationId: Long,
        balance: BigDecimal,
        balanceDate: LocalDate,
    ) {
        val entity = jpaRepository.findByIbanAndOrganizationId(iban, organizationId) ?: return
        entity.balance = balance
        entity.balanceDate = balanceDate
        jpaRepository.save(entity)
    }

    private fun Account.toEntity(organizationId: Long) =
        AccountEntity(
            iban = iban,
            name = name,
            organization = organizationJpaRepository.getReferenceById(organizationId),
        )

    private fun AccountEntity.toDomain() =
        Account(
            iban = iban,
            name = name,
            balance = balance,
            balanceDate = balanceDate,
        )
}
