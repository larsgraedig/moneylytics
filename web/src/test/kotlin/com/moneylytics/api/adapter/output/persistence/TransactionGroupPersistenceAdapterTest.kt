package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TransactionGroupPersistenceAdapterTest {
    private val groupJpaRepository: TransactionGroupJpaRepository = mock()
    private val organizationJpaRepository: OrganizationJpaRepository = mock()
    private val memberJpaRepository: TransactionGroupMemberJpaRepository = mock()
    private val adapter = TransactionGroupPersistenceAdapter(groupJpaRepository, organizationJpaRepository, memberJpaRepository)

    private val organizationId = 1L
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)

    @Test
    fun `should map group entity to domain`() {
        val entity = TransactionGroupEntity(organization = organizationEntity, name = "Rückerstattung", comment = "Arzt", id = 10L)
        whenever(groupJpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(entity))

        val result = adapter.findAllByOrganizationId(organizationId)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(10L)
        assertThat(result[0].organizationId).isEqualTo(organizationId)
        assertThat(result[0].name).isEqualTo("Rückerstattung")
        assertThat(result[0].comment).isEqualTo("Arzt")
    }

    @Test
    fun `should add member when not already exists`() {
        val pk = TransactionGroupMemberEntity.PK(groupId = 10L, transactionId = 5L)
        whenever(memberJpaRepository.existsById(pk)).thenReturn(false)

        adapter.addMember(10L, 5L)

        val captor = argumentCaptor<TransactionGroupMemberEntity>()
        verify(memberJpaRepository).saveAndFlush(captor.capture())
        assertThat(captor.firstValue.groupId).isEqualTo(10L)
        assertThat(captor.firstValue.transactionId).isEqualTo(5L)
    }

    @Test
    fun `should skip addMember when member already exists`() {
        val pk = TransactionGroupMemberEntity.PK(groupId = 10L, transactionId = 5L)
        whenever(memberJpaRepository.existsById(pk)).thenReturn(true)

        adapter.addMember(10L, 5L)

        verify(memberJpaRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `should set name and comment to null when blank on update`() {
        val entity = TransactionGroupEntity(organization = organizationEntity, name = "Old", comment = "Old Comment", id = 10L)
        whenever(groupJpaRepository.findByIdAndOrganizationId(10L, organizationId)).thenReturn(entity)

        adapter.update(10L, organizationId, name = "  ", comment = "")

        assertThat(entity.name).isNull()
        assertThat(entity.comment).isNull()
        verify(groupJpaRepository).save(entity)
    }

    @Test
    fun `should update name and comment on update`() {
        val entity = TransactionGroupEntity(organization = organizationEntity, id = 10L)
        whenever(groupJpaRepository.findByIdAndOrganizationId(10L, organizationId)).thenReturn(entity)

        adapter.update(10L, organizationId, name = "Neu", comment = "Notiz")

        assertThat(entity.name).isEqualTo("Neu")
        assertThat(entity.comment).isEqualTo("Notiz")
    }

    @Test
    fun `should do nothing on update when group not found`() {
        whenever(groupJpaRepository.findByIdAndOrganizationId(99L, organizationId)).thenReturn(null)

        adapter.update(99L, organizationId, name = "Test", comment = null)

        verify(groupJpaRepository, never()).save(any())
    }

    @Test
    fun `should delegate removeMember to repository`() {
        adapter.removeMember(10L, 5L)

        verify(memberJpaRepository).deleteByGroupIdAndTransactionId(10L, 5L)
    }
}
