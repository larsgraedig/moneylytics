package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AccountPersistenceAdapterTest {
    private val jpaRepository: AccountJpaRepository = mock()
    private val organizationJpaRepository: OrganizationJpaRepository = mock()
    private val adapter = AccountPersistenceAdapter(jpaRepository, organizationJpaRepository)

    private val organizationId = 1L
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)
    private val entity = AccountEntity(iban = "DE01", name = "Giro", organization = organizationEntity, id = 10L)

    @Test
    fun `should return null when account not found by IBAN`() {
        whenever(jpaRepository.findByIbanAndOrganizationId("DE01", organizationId)).thenReturn(null)

        val result = adapter.findByIban("DE01", organizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should map entity to domain account`() {
        whenever(jpaRepository.findByIbanAndOrganizationId("DE01", organizationId)).thenReturn(entity)

        val result = adapter.findByIban("DE01", organizationId)

        assertThat(result).isNotNull
        assertThat(result!!.iban).isEqualTo("DE01")
        assertThat(result.name).isEqualTo("Giro")
    }

    @Test
    fun `should save entity and return mapped domain`() {
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)
        whenever(jpaRepository.save(any())).thenReturn(entity)

        val result =
            adapter.save(
                com.moneylytics.api.domain
                    .Account(iban = "DE01", name = "Giro"),
                organizationId,
            )

        assertThat(result.iban).isEqualTo("DE01")
        assertThat(result.name).isEqualTo("Giro")
    }

    @Test
    fun `should map all accounts for organization`() {
        val entity2 = AccountEntity(iban = "DE02", name = "Sparkonto", organization = organizationEntity, id = 11L)
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(entity, entity2))

        val result = adapter.findAll(organizationId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.iban }).containsExactlyInAnyOrder("DE01", "DE02")
    }

    @Test
    fun `should delegate delete by IBAN and organizationId`() {
        adapter.delete("DE01", organizationId)

        verify(jpaRepository).deleteByIbanAndOrganizationId("DE01", organizationId)
    }
}
