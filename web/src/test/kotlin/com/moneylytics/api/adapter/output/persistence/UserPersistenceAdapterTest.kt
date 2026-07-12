package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserPersistenceAdapterTest {
    private val jpaRepository: UserJpaRepository = mock()
    private val accountJpaRepository: AccountJpaRepository = mock()
    private val adapter = UserPersistenceAdapter(jpaRepository, accountJpaRepository)

    private val userId = 1L
    private val userEntity = UserEntity(externalId = "test@test.de", id = userId)

    @Test
    fun `should return null when user not found by externalId`() {
        whenever(jpaRepository.findByExternalId("unknown")).thenReturn(null)

        val result = adapter.findByExternalId("unknown")

        assertThat(result).isNull()
    }

    @Test
    fun `should map entity to domain user`() {
        whenever(jpaRepository.findByExternalId("test@test.de")).thenReturn(userEntity)

        val result = adapter.findByExternalId("test@test.de")

        assertThat(result).isEqualTo(User(id = userId, externalId = "test@test.de", passwordHash = null))
    }

    @Test
    fun `should create new user when not found`() {
        val newEntity = UserEntity(externalId = "new@test.de", passwordHash = "hash", id = 2L)
        whenever(jpaRepository.findByExternalId("new@test.de")).thenReturn(null)
        whenever(jpaRepository.save(any())).thenReturn(newEntity)

        val result = adapter.save("new@test.de", "hash")

        assertThat(result.id).isEqualTo(2L)
        assertThat(result.externalId).isEqualTo("new@test.de")
    }

    @Test
    fun `should update password hash on existing user`() {
        val existing = UserEntity(externalId = "test@test.de", passwordHash = "old", id = userId)
        val updated = UserEntity(externalId = "test@test.de", passwordHash = "new", id = userId)
        whenever(jpaRepository.findByExternalId("test@test.de")).thenReturn(existing)
        whenever(jpaRepository.save(existing)).thenReturn(updated)

        val result = adapter.save("test@test.de", "new")

        assertThat(result.passwordHash).isEqualTo("new")
    }

    @Test
    fun `should parse comma-separated column order in getSettings`() {
        val entity = UserEntity(externalId = "test@test.de", transactionsColumnOrder = "date,amount,category", id = userId)
        whenever(jpaRepository.getReferenceById(userId)).thenReturn(entity)

        val settings = adapter.getSettings(userId)

        assertThat(settings.transactionsColumnOrder).containsExactly("date", "amount", "category")
    }

    @Test
    fun `should return null column order when not set in entity`() {
        whenever(jpaRepository.getReferenceById(userId)).thenReturn(userEntity)

        val settings = adapter.getSettings(userId)

        assertThat(settings.transactionsColumnOrder).isNull()
    }

    @Test
    fun `should return null column order when all entries are blank after filtering`() {
        val entity = UserEntity(externalId = "test@test.de", transactionsColumnOrder = " , ", id = userId)
        whenever(jpaRepository.getReferenceById(userId)).thenReturn(entity)

        val settings = adapter.getSettings(userId)

        assertThat(settings.transactionsColumnOrder).isNull()
    }

    @Test
    fun `should set default account by IBAN lookup in updateSettings`() {
        val accountEntity = AccountEntity(iban = "DE01", name = "Giro", user = userEntity, id = 5L)
        val entityToUpdate = UserEntity(externalId = "test@test.de", id = userId)
        val saved = UserEntity(externalId = "test@test.de", defaultAccount = accountEntity, id = userId)
        whenever(jpaRepository.getReferenceById(userId)).thenReturn(entityToUpdate)
        whenever(accountJpaRepository.findByIbanAndUserId("DE01", userId)).thenReturn(accountEntity)
        whenever(jpaRepository.save(entityToUpdate)).thenReturn(saved)

        val settings = adapter.updateSettings(userId, defaultAccountIban = "DE01", language = null, transactionsColumnOrder = null)

        assertThat(settings.defaultAccountIban).isEqualTo("DE01")
    }

    @Test
    fun `should set default account to null when IBAN is null in updateSettings`() {
        val entityToUpdate = UserEntity(externalId = "test@test.de", id = userId)
        whenever(jpaRepository.getReferenceById(userId)).thenReturn(entityToUpdate)
        whenever(jpaRepository.save(entityToUpdate)).thenReturn(entityToUpdate)

        val settings = adapter.updateSettings(userId, defaultAccountIban = null, language = null, transactionsColumnOrder = null)

        assertThat(settings.defaultAccountIban).isNull()
    }
}
