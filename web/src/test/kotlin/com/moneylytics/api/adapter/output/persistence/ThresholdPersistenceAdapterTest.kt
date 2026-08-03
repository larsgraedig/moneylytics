package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class ThresholdPersistenceAdapterTest {
    private val jpaRepository: ThresholdJpaRepository = mock()
    private val organizationJpaRepository: OrganizationJpaRepository = mock()
    private val categoryJpaRepository: CategoryJpaRepository = mock()
    private val adapter = ThresholdPersistenceAdapter(jpaRepository, organizationJpaRepository, categoryJpaRepository)

    private val organizationId = 1L
    private val categoryId = 10L
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)
    private val parentCategoryEntity =
        CategoryEntity(name = "Lebensmittel", parent = null, organization = organizationEntity, id = categoryId)
    private val childCategoryEntity =
        CategoryEntity(name = "Supermarkt", parent = parentCategoryEntity, organization = organizationEntity, id = 11L)

    @Test
    fun `should map entity with category to domain threshold including path`() {
        val entity =
            ThresholdEntity(
                organization = organizationEntity,
                categoryEntity = childCategoryEntity,
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("80"),
                warning = BigDecimal("120"),
                critical = BigDecimal("200"),
                id = 1L,
            )
        whenever(jpaRepository.findByOrganizationIdAndCategoryEntityIsNotNull(organizationId)).thenReturn(listOf(entity))

        val result = adapter.findAllByOrganizationId(organizationId)

        assertThat(result).hasSize(1)
        val t = result[0]
        assertThat(t.id).isEqualTo(1L)
        assertThat(t.categoryId).isEqualTo(11L)
        assertThat(t.categoryPath).containsExactly("Lebensmittel", "Supermarkt")
        assertThat(t.period).isEqualTo(ThresholdPeriod.MONTHLY)
        assertThat(t.notice).isEqualByComparingTo(BigDecimal("80"))
        assertThat(t.critical).isEqualByComparingTo(BigDecimal("200"))
    }

    @Test
    fun `should exclude entities without category_id from results`() {
        whenever(jpaRepository.findByOrganizationIdAndCategoryEntityIsNotNull(organizationId)).thenReturn(emptyList())

        val result = adapter.findAllByOrganizationId(organizationId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should create new threshold when none exists for category and period`() {
        whenever(
            jpaRepository.findByOrganizationIdAndCategoryEntityIdAndPeriod(
                organizationId,
                categoryId,
                ThresholdPeriod.MONTHLY,
            ),
        ).thenReturn(null)
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)
        whenever(categoryJpaRepository.getReferenceById(categoryId)).thenReturn(parentCategoryEntity)
        val saved =
            ThresholdEntity(
                organization = organizationEntity,
                categoryEntity = parentCategoryEntity,
                period = ThresholdPeriod.MONTHLY,
                critical = BigDecimal("100"),
                id = 5L,
            )
        whenever(jpaRepository.save(any())).thenReturn(saved)

        val threshold =
            Threshold(
                id = 0L,
                categoryId = categoryId,
                categoryPath = listOf("Lebensmittel"),
                period = ThresholdPeriod.MONTHLY,
                notice = null,
                warning = null,
                critical = BigDecimal("100"),
            )
        val result = adapter.upsert(threshold, organizationId)

        assertThat(result.id).isEqualTo(5L)
        assertThat(result.critical).isEqualByComparingTo(BigDecimal("100"))
    }

    @Test
    fun `should update existing threshold when found by categoryId and period`() {
        val existing =
            ThresholdEntity(
                organization = organizationEntity,
                categoryEntity = parentCategoryEntity,
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("50"),
                id = 5L,
            )
        whenever(
            jpaRepository.findByOrganizationIdAndCategoryEntityIdAndPeriod(
                organizationId,
                categoryId,
                ThresholdPeriod.MONTHLY,
            ),
        ).thenReturn(existing)
        whenever(jpaRepository.save(existing)).thenReturn(existing)

        val threshold =
            Threshold(
                id = 5L,
                categoryId = categoryId,
                categoryPath = listOf("Lebensmittel"),
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("80"),
                warning = null,
                critical = null,
            )
        adapter.upsert(threshold, organizationId)

        verify(categoryJpaRepository, never()).getReferenceById(any())
        assertThat(existing.notice).isEqualByComparingTo(BigDecimal("80"))
    }

    @Test
    fun `should delegate deleteThreshold to repository`() {
        adapter.deleteByIdAndOrganizationId(3L, organizationId)

        verify(jpaRepository).deleteByIdAndOrganizationId(3L, organizationId)
    }
}
