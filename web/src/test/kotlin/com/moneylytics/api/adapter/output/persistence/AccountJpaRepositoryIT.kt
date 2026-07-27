package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AccountJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Test
    fun `should find account by iban and user id when account exists`() {
        val result = accountRepo.findByIbanAndOrganizationId("DE00TEST000000000001", organizationId)

        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(account.id)
    }

    @Test
    fun `should return null when account belongs to different user`() {
        val result = accountRepo.findByIbanAndOrganizationId("DE00TEST000000000001", otherOrganizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should return null when iban does not exist`() {
        val result = accountRepo.findByIbanAndOrganizationId("DE99UNKNOWN00000000", organizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should find all accounts for user only`() {
        val otherAccount =
            accountRepo.save(
                AccountEntity(iban = "DE00OTHER000000000002", name = "Fremdes Konto", organization = otherOrganization),
            )

        val result = accountRepo.findAllByOrganizationId(organizationId)

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo(account.id)
        assertThat(result).doesNotContain(otherAccount)
    }

    @Test
    fun `should return empty list when user has no accounts`() {
        val result = accountRepo.findAllByOrganizationId(otherOrganizationId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should delete account by iban and user id`() {
        accountRepo.deleteByIbanAndOrganizationId("DE00TEST000000000001", organizationId)
        flushAndClear()

        assertThat(accountRepo.findByIbanAndOrganizationId("DE00TEST000000000001", organizationId)).isNull()
    }

    @Test
    fun `should not delete account belonging to different user`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER000000000002", name = "Fremd", organization = otherOrganization))
        val otherAccountId = checkNotNull(otherAccount.id)

        accountRepo.deleteByIbanAndOrganizationId("DE00OTHER000000000002", organizationId)
        flushAndClear()

        assertThat(accountRepo.findById(otherAccountId)).isPresent
    }
}
