package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.Category
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
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
        val entity =
            CategoryEntity(
                name = "Lebensmittel",
                subcategory = "Supermarkt",
                groupName = "Konsum",
                organization = organizationEntity,
                id = 1L,
            )
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(entity))

        val result = adapter.findAll(organizationId)

        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Lebensmittel")
        assertThat(result[0].subcategory).isEqualTo("Supermarkt")
        assertThat(result[0].group).isEqualTo("Konsum")
    }

    @Test
    fun `should save only categories not already present`() {
        val existing =
            CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", groupName = null, organization = organizationEntity, id = 1L)
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(existing))
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)

        val toSave =
            listOf(
                Category(name = "Lebensmittel", subcategory = "Supermarkt", group = null),
                Category(name = "Transport", subcategory = "ÖPNV", group = null),
            )
        adapter.saveAllIfAbsent(toSave, organizationId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(1)
        assertThat(captor.firstValue[0].name).isEqualTo("Transport")
    }

    @Test
    fun `should save nothing when all categories already exist`() {
        val existing =
            CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", groupName = null, organization = organizationEntity, id = 1L)
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(existing))
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)

        adapter.saveAllIfAbsent(listOf(Category(name = "Lebensmittel", subcategory = "Supermarkt", group = null)), organizationId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).isEmpty()
    }

    @Test
    fun `should save all when none exist yet`() {
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(emptyList())
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)
        val categories =
            listOf(
                Category(name = "Transport", subcategory = "ÖPNV", group = null),
                Category(name = "Freizeit", subcategory = "Kino", group = null),
            )

        adapter.saveAllIfAbsent(categories, organizationId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(2)
    }

    @Test
    fun `should distinguish categories by group when deduplicating`() {
        val existingNoGroup =
            CategoryEntity(
                name = "Lebensmittel",
                subcategory = "Supermarkt",
                groupName = null,
                organization = organizationEntity,
                id = 1L,
            )
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(existingNoGroup))
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)

        val withGroup = listOf(Category(name = "Lebensmittel", subcategory = "Supermarkt", group = "Konsum"))
        adapter.saveAllIfAbsent(withGroup, organizationId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(1)
    }
}
