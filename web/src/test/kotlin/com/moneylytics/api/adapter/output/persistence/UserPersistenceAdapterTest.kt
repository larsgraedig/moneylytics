package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.Role
import com.moneylytics.api.domain.Tier
import com.moneylytics.api.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserPersistenceAdapterTest {
    private val jpaRepository: UserJpaRepository = mock()
    private val accountJpaRepository: AccountJpaRepository = mock()
    private val tierJpaRepository: CustomerTierJpaRepository = mock()
    private val adapter = UserPersistenceAdapter(jpaRepository, accountJpaRepository, tierJpaRepository)

    private val userId = 1L
    private val tierEntity = CustomerTierEntity(name = "Standard", isDefault = true, id = 10L)
    private val tier = Tier(id = 10L, name = "Standard", description = null, active = true, isDefault = true)
    private val userEntity = UserEntity(externalId = "test@test.de", tier = tierEntity, id = userId)

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

        assertThat(result).isEqualTo(User(id = userId, externalId = "test@test.de", passwordHash = null, role = Role.USER, tier = tier))
    }

    @Test
    fun `should create new user when not found`() {
        val newEntity = UserEntity(externalId = "new@test.de", passwordHash = "hash", tier = tierEntity, id = 2L)
        whenever(tierJpaRepository.findByIsDefaultTrue()).thenReturn(tierEntity)
        whenever(jpaRepository.findByExternalId("new@test.de")).thenReturn(null)
        whenever(jpaRepository.save(any())).thenReturn(newEntity)

        val result = adapter.save("new@test.de", "hash")

        assertThat(result.id).isEqualTo(2L)
        assertThat(result.externalId).isEqualTo("new@test.de")
    }

    @Test
    fun `should update password hash on existing user`() {
        val existing = UserEntity(externalId = "test@test.de", passwordHash = "old", tier = tierEntity, id = userId)
        val updated = UserEntity(externalId = "test@test.de", passwordHash = "new", tier = tierEntity, id = userId)
        whenever(tierJpaRepository.findByIsDefaultTrue()).thenReturn(tierEntity)
        whenever(jpaRepository.findByExternalId("test@test.de")).thenReturn(existing)
        whenever(jpaRepository.save(existing)).thenReturn(updated)

        val result = adapter.save("test@test.de", "new")

        assertThat(result.passwordHash).isEqualTo("new")
    }

    @Test
    fun `should parse comma-separated column order in getSettings`() {
        val entity =
            UserEntity(externalId = "test@test.de", tier = tierEntity, transactionsColumnOrder = "date,amount,category", id = userId)
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
        val entity = UserEntity(externalId = "test@test.de", tier = tierEntity, transactionsColumnOrder = " , ", id = userId)
        whenever(jpaRepository.getReferenceById(userId)).thenReturn(entity)

        val settings = adapter.getSettings(userId)

        assertThat(settings.transactionsColumnOrder).isNull()
    }

    @Test
    fun `should set default account by IBAN lookup in updateSettings`() {
        val orgId = 10L
        val orgEntity = OrganizationEntity(name = "Org", id = orgId)
        val accountEntity = AccountEntity(iban = "DE01", name = "Giro", organization = orgEntity, id = 5L)
        val entityToUpdate = UserEntity(externalId = "test@test.de", tier = tierEntity, id = userId)
        val saved = UserEntity(externalId = "test@test.de", tier = tierEntity, defaultAccount = accountEntity, id = userId)
        whenever(jpaRepository.getReferenceById(userId)).thenReturn(entityToUpdate)
        whenever(accountJpaRepository.findByIbanAndOrganizationId("DE01", orgId)).thenReturn(accountEntity)
        whenever(jpaRepository.save(entityToUpdate)).thenReturn(saved)

        val settings =
            adapter.updateSettings(
                userId,
                organizationId = orgId,
                defaultAccountIban = "DE01",
                language = null,
                transactionsColumnOrder = null,
            )

        assertThat(settings.defaultAccountIban).isEqualTo("DE01")
    }

    @Test
    fun `should set default account to null when IBAN is null in updateSettings`() {
        val orgId = 10L
        val entityToUpdate = UserEntity(externalId = "test@test.de", tier = tierEntity, id = userId)
        whenever(jpaRepository.getReferenceById(userId)).thenReturn(entityToUpdate)
        whenever(jpaRepository.save(entityToUpdate)).thenReturn(entityToUpdate)

        val settings =
            adapter.updateSettings(
                userId,
                organizationId = orgId,
                defaultAccountIban = null,
                language = null,
                transactionsColumnOrder = null,
            )

        assertThat(settings.defaultAccountIban).isNull()
    }

    @Test
    fun `should map SYSTEM_ADMIN role from entity to domain user`() {
        val adminEntity = UserEntity(externalId = "admin@test.de", tier = tierEntity, role = Role.SYSTEM_ADMIN, id = userId)
        whenever(jpaRepository.findByExternalId("admin@test.de")).thenReturn(adminEntity)

        val result = adapter.findByExternalId("admin@test.de")

        assertThat(result?.role).isEqualTo(Role.SYSTEM_ADMIN)
    }

    @Test
    fun `should set role to SYSTEM_ADMIN when promoteToSystemAdmin is called`() {
        val entity = UserEntity(externalId = "test@test.de", tier = tierEntity, id = userId)
        whenever(jpaRepository.getReferenceById(userId)).thenReturn(entity)

        adapter.promoteToSystemAdmin(userId)

        assertThat(entity.role).isEqualTo(Role.SYSTEM_ADMIN)
        verify(jpaRepository).getReferenceById(userId)
    }
}
