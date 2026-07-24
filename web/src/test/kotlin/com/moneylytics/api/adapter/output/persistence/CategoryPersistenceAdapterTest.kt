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
    private val userJpaRepository: UserJpaRepository = mock()
    private val adapter = CategoryPersistenceAdapter(jpaRepository, userJpaRepository)

    private val userId = 1L
    private val userEntity = UserEntity(externalId = "test@test.de", id = userId)

    @Test
    fun `should map entity to domain category`() {
        val entity = CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", user = userEntity, categoryGroup = "Konsum", id = 1L)
        whenever(jpaRepository.findAllByUserId(userId)).thenReturn(listOf(entity))

        val result = adapter.findAll(userId)

        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Lebensmittel")
        assertThat(result[0].subcategory).isEqualTo("Supermarkt")
        assertThat(result[0].group).isEqualTo("Konsum")
    }

    @Test
    fun `should save only categories not already present`() {
        val existing = CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", user = userEntity, id = 1L)
        whenever(jpaRepository.findAllByUserId(userId)).thenReturn(listOf(existing))
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)

        val toSave =
            listOf(
                Category(name = "Lebensmittel", subcategory = "Supermarkt"),
                Category(name = "Transport", subcategory = "ÖPNV"),
            )
        adapter.saveAllIfAbsent(toSave, userId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(1)
        assertThat(captor.firstValue[0].name).isEqualTo("Transport")
    }

    @Test
    fun `should save nothing when all categories already exist`() {
        val existing = CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", user = userEntity, id = 1L)
        whenever(jpaRepository.findAllByUserId(userId)).thenReturn(listOf(existing))
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)

        adapter.saveAllIfAbsent(listOf(Category(name = "Lebensmittel", subcategory = "Supermarkt")), userId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).isEmpty()
    }

    @Test
    fun `should save all when none exist yet`() {
        whenever(jpaRepository.findAllByUserId(userId)).thenReturn(emptyList())
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)
        val categories =
            listOf(
                Category(name = "Transport", subcategory = "ÖPNV"),
                Category(name = "Freizeit", subcategory = "Kino"),
            )

        adapter.saveAllIfAbsent(categories, userId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(2)
    }

    @Test
    fun `should distinguish categories by group when deduplicating`() {
        val existingNoGroup =
            CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", user = userEntity, categoryGroup = null, id = 1L)
        whenever(jpaRepository.findAllByUserId(userId)).thenReturn(listOf(existingNoGroup))
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)

        val withGroup = listOf(Category(name = "Lebensmittel", subcategory = "Supermarkt", group = "Konsum"))
        adapter.saveAllIfAbsent(withGroup, userId)

        val captor = argumentCaptor<List<CategoryEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(1)
    }
}
