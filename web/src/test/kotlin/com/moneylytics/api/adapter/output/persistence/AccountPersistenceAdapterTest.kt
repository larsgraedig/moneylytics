package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AccountPersistenceAdapterTest {
    private val jpaRepository: AccountJpaRepository = mock()
    private val userJpaRepository: UserJpaRepository = mock()
    private val adapter = AccountPersistenceAdapter(jpaRepository, userJpaRepository)

    private val userId = 1L
    private val userEntity = UserEntity(externalId = "test@test.de", id = userId)
    private val entity = AccountEntity(iban = "DE01", name = "Giro", user = userEntity, id = 10L)

    @Test
    fun `should return null when account not found by IBAN`() {
        whenever(jpaRepository.findByIbanAndUserId("DE01", userId)).thenReturn(null)

        val result = adapter.findByIban("DE01", userId)

        assertThat(result).isNull()
    }

    @Test
    fun `should map entity to domain account`() {
        whenever(jpaRepository.findByIbanAndUserId("DE01", userId)).thenReturn(entity)

        val result = adapter.findByIban("DE01", userId)

        assertThat(result).isNotNull
        assertThat(result!!.iban).isEqualTo("DE01")
        assertThat(result.name).isEqualTo("Giro")
    }

    @Test
    fun `should save entity and return mapped domain`() {
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)
        whenever(jpaRepository.save(any())).thenReturn(entity)

        val result =
            adapter.save(
                com.moneylytics.api.domain
                    .Account(iban = "DE01", name = "Giro"),
                userId,
            )

        assertThat(result.iban).isEqualTo("DE01")
        assertThat(result.name).isEqualTo("Giro")
    }

    @Test
    fun `should map all accounts for user`() {
        val entity2 = AccountEntity(iban = "DE02", name = "Sparkonto", user = userEntity, id = 11L)
        whenever(jpaRepository.findAllByUserId(userId)).thenReturn(listOf(entity, entity2))

        val result = adapter.findAll(userId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.iban }).containsExactlyInAnyOrder("DE01", "DE02")
    }

    @Test
    fun `should delegate delete by IBAN and userId`() {
        adapter.delete("DE01", userId)

        verify(jpaRepository).deleteByIbanAndUserId("DE01", userId)
    }
}
