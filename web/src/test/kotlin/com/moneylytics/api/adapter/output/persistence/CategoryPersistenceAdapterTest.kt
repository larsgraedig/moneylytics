package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.Category
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CategoryPersistenceAdapterTest {
    private val jpaRepository: CategoryJpaRepository = mock()
    private val organizationJpaRepository: OrganizationJpaRepository = mock()
    private val adapter = CategoryPersistenceAdapter(jpaRepository, organizationJpaRepository)

    private val organizationId = 1L
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)

    @Test
    fun `should map entity to domain category`() {
        val rootEntity = CategoryEntity(name = "Lebensmittel", parent = null, organization = organizationEntity, id = 10L)
        val childEntity = CategoryEntity(name = "Supermarkt", parent = rootEntity, organization = organizationEntity, id = 1L)
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(rootEntity, childEntity))

        val result = adapter.findAll(organizationId)

        assertThat(result).hasSize(2)
        val child = result.first { it.name == "Supermarkt" }
        assertThat(child.parentId).isEqualTo(10L)
        val root = result.first { it.name == "Lebensmittel" }
        assertThat(root.parentId).isNull()
    }

    @Test
    fun `should return existing node when path already exists`() {
        val rootEntity = CategoryEntity(name = "Lebensmittel", parent = null, organization = organizationEntity, id = 10L)
        val childEntity = CategoryEntity(name = "Supermarkt", parent = rootEntity, organization = organizationEntity, id = 1L)
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)
        whenever(jpaRepository.findByNameAndParentIdAndOrganizationId("Lebensmittel", null, organizationId)).thenReturn(rootEntity)
        whenever(jpaRepository.findByNameAndParentIdAndOrganizationId("Supermarkt", 10L, organizationId)).thenReturn(childEntity)

        val result = adapter.findOrCreate(listOf("Lebensmittel", "Supermarkt"), organizationId)

        assertThat(result).isEqualTo(Category(id = 1L, name = "Supermarkt", parentId = 10L))
        verify(jpaRepository, never()).save(any())
    }

    @Test
    fun `should create missing nodes along the path`() {
        val rootEntity = CategoryEntity(name = "Lebensmittel", parent = null, organization = organizationEntity, id = 10L)
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)
        whenever(jpaRepository.findByNameAndParentIdAndOrganizationId("Lebensmittel", null, organizationId)).thenReturn(null)
        whenever(jpaRepository.findByNameAndParentIdAndOrganizationId("Supermarkt", 10L, organizationId)).thenReturn(null)
        whenever(jpaRepository.save(any<CategoryEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as CategoryEntity
            if (entity.name == "Lebensmittel") {
                CategoryEntity(name = "Lebensmittel", parent = null, organization = organizationEntity, id = 10L)
            } else {
                CategoryEntity(name = "Supermarkt", parent = rootEntity, organization = organizationEntity, id = 99L)
            }
        }
        whenever(jpaRepository.getReferenceById(10L)).thenReturn(rootEntity)

        val result = adapter.findOrCreate(listOf("Lebensmittel", "Supermarkt"), organizationId)

        assertThat(result.name).isEqualTo("Supermarkt")
        assertThat(result.id).isEqualTo(99L)
        assertThat(result.parentId).isEqualTo(10L)
    }

    @Test
    fun `should create root node for single-element path`() {
        val rootEntity = CategoryEntity(name = "Transport", parent = null, organization = organizationEntity, id = 5L)
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)
        whenever(jpaRepository.findByNameAndParentIdAndOrganizationId("Transport", null, organizationId)).thenReturn(null)
        whenever(jpaRepository.save(any<CategoryEntity>())).thenReturn(rootEntity)

        val result = adapter.findOrCreate(listOf("Transport"), organizationId)

        assertThat(result).isEqualTo(Category(id = 5L, name = "Transport", parentId = null))
        val captor = argumentCaptor<CategoryEntity>()
        verify(jpaRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("Transport")
        assertThat(captor.firstValue.parent).isNull()
    }

    @Test
    fun `should update parent when moving category`() {
        val rootEntity = CategoryEntity(name = "Lebensmittel", parent = null, organization = organizationEntity, id = 10L)
        val newParentEntity = CategoryEntity(name = "Freizeit", parent = null, organization = organizationEntity, id = 20L)
        whenever(jpaRepository.findByIdAndOrganizationId(10L, organizationId)).thenReturn(rootEntity)
        whenever(jpaRepository.getReferenceById(20L)).thenReturn(newParentEntity)
        whenever(jpaRepository.save(any<CategoryEntity>())).thenAnswer { it.arguments[0] }

        adapter.move(10L, 20L, organizationId)

        val captor = argumentCaptor<CategoryEntity>()
        verify(jpaRepository).save(captor.capture())
        assertThat(captor.firstValue.parent?.id).isEqualTo(20L)
    }

    @Test
    fun `should set parent to null when moving category to root`() {
        val parentEntity = CategoryEntity(name = "Freizeit", parent = null, organization = organizationEntity, id = 20L)
        val entity = CategoryEntity(name = "Kino", parent = parentEntity, organization = organizationEntity, id = 5L)
        whenever(jpaRepository.findByIdAndOrganizationId(5L, organizationId)).thenReturn(entity)
        whenever(jpaRepository.save(any<CategoryEntity>())).thenAnswer { it.arguments[0] }

        adapter.move(5L, null, organizationId)

        val captor = argumentCaptor<CategoryEntity>()
        verify(jpaRepository).save(captor.capture())
        assertThat(captor.firstValue.parent).isNull()
    }

    @Test
    fun `should throw when moving unknown category`() {
        whenever(jpaRepository.findByIdAndOrganizationId(99L, organizationId)).thenReturn(null)

        assertThatThrownBy { adapter.move(99L, null, organizationId) }
            .isInstanceOf(NoSuchElementException::class.java)
        verify(jpaRepository, never()).save(any())
    }
}
