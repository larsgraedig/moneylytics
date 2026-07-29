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
                subcategory = "Konsum",
                groupName = "Supermarkt",
                organization = organizationEntity,
                id = 1L,
            )
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(entity))

        val result = adapter.findAll(organizationId)

        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Lebensmittel")
        assertThat(result[0].subcategory).isEqualTo("Konsum")
        assertThat(result[0].group).isEqualTo("Supermarkt")
    }

    @Test
    fun `should save only categories not already present`() {
        val existing =
            CategoryEntity(name = "Lebensmittel", subcategory = null, groupName = "Supermarkt", organization = organizationEntity, id = 1L)
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(existing))
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)

        val toSave =
            listOf(
                Category(name = "Lebensmittel", subcategory = null, group = "Supermarkt"),
                Category(name = "Transport", subcategory = null, group = "ÖPNV"),
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
            CategoryEntity(name = "Lebensmittel", subcategory = null, groupName = "Supermarkt", organization = organizationEntity, id = 1L)
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(existing))
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)

        adapter.saveAllIfAbsent(listOf(Category(name = "Lebensmittel", subcategory = null, group = "Supermarkt")), organizationId)

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
                Category(name = "Transport", subcategory = null, group = "ÖPNV"),
                Category(name = "Freizeit", subcategory = null, group = "Kino"),
            )

        adapter.saveAllIfAbsent(categories, organizationId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(2)
    }

    @Test
    fun `should distinguish categories by subcategory when deduplicating`() {
        val existingNoSub =
            CategoryEntity(
                name = "Lebensmittel",
                subcategory = null,
                groupName = "Supermarkt",
                organization = organizationEntity,
                id = 1L,
            )
        whenever(jpaRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(existingNoSub))
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)

        val withSub = listOf(Category(name = "Lebensmittel", subcategory = "Konsum", group = "Supermarkt"))
        adapter.saveAllIfAbsent(withSub, organizationId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(1)
    }
}
