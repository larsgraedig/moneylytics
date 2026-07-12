package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AccountJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Test
    fun `should find account by iban and user id when account exists`() {
        val result = accountRepo.findByIbanAndUserId("DE00TEST000000000001", user.id!!)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(account.id)
    }

    @Test
    fun `should return null when account belongs to different user`() {
        val result = accountRepo.findByIbanAndUserId("DE00TEST000000000001", otherUser.id!!)

        assertThat(result).isNull()
    }

    @Test
    fun `should return null when iban does not exist`() {
        val result = accountRepo.findByIbanAndUserId("DE99UNKNOWN00000000", user.id!!)

        assertThat(result).isNull()
    }

    @Test
    fun `should find all accounts for user only`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER000000000002", name = "Fremdes Konto", user = otherUser))

        val result = accountRepo.findAllByUserId(user.id!!)

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo(account.id)
        assertThat(result).doesNotContain(otherAccount)
    }

    @Test
    fun `should return empty list when user has no accounts`() {
        val result = accountRepo.findAllByUserId(otherUser.id!!)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should delete account by iban and user id`() {
        accountRepo.deleteByIbanAndUserId("DE00TEST000000000001", user.id!!)
        flushAndClear()

        assertThat(accountRepo.findByIbanAndUserId("DE00TEST000000000001", user.id!!)).isNull()
    }

    @Test
    fun `should not delete account belonging to different user`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER000000000002", name = "Fremd", user = otherUser))

        accountRepo.deleteByIbanAndUserId("DE00OTHER000000000002", user.id!!)
        flushAndClear()

        assertThat(accountRepo.findById(otherAccount.id!!)).isPresent
    }
}
