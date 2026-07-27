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
    private val adapter = ThresholdPersistenceAdapter(jpaRepository, organizationJpaRepository)

    private val organizationId = 1L
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)

    @Test
    fun `should map entity to domain threshold`() {
        val entity =
            ThresholdEntity(
                organization = organizationEntity,
                category = "Lebensmittel",
                subcategory = "Supermarkt",
                categoryGroup = "Konsum",
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("80"),
                warning = BigDecimal("120"),
                critical = BigDecimal("200"),
                id = 1L,
            )
        whenever(jpaRepository.findByOrganizationId(organizationId)).thenReturn(listOf(entity))

        val result = adapter.findAllByOrganizationId(organizationId)

        assertThat(result).hasSize(1)
        val t = result[0]
        assertThat(t.id).isEqualTo(1L)
        assertThat(t.category).isEqualTo("Lebensmittel")
        assertThat(t.subcategory).isEqualTo("Supermarkt")
        assertThat(t.categoryGroup).isEqualTo("Konsum")
        assertThat(t.period).isEqualTo(ThresholdPeriod.MONTHLY)
        assertThat(t.notice).isEqualByComparingTo(BigDecimal("80"))
        assertThat(t.critical).isEqualByComparingTo(BigDecimal("200"))
    }

    @Test
    fun `should create new threshold when none exists for category`() {
        whenever(
            jpaRepository.findByOrganizationIdAndCategoryAndSubcategoryIsNullAndPeriod(
                organizationId,
                "Transport",
                ThresholdPeriod.MONTHLY,
            ),
        ).thenReturn(null)
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)
        val saved =
            ThresholdEntity(
                organization = organizationEntity,
                category = "Transport",
                period = ThresholdPeriod.MONTHLY,
                critical = BigDecimal("100"),
                id = 5L,
            )
        whenever(jpaRepository.save(any())).thenReturn(saved)

        val threshold =
            Threshold(
                id = 0L,
                category = "Transport",
                subcategory = null,
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
    fun `should update existing threshold when found by category and null subcategory`() {
        val existing =
            ThresholdEntity(
                organization = organizationEntity,
                category = "Transport",
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("50"),
                id = 5L,
            )
        whenever(
            jpaRepository.findByOrganizationIdAndCategoryAndSubcategoryIsNullAndPeriod(
                organizationId,
                "Transport",
                ThresholdPeriod.MONTHLY,
            ),
        ).thenReturn(existing)
        whenever(jpaRepository.save(existing)).thenReturn(existing)

        val threshold =
            Threshold(
                id = 5L,
                category = "Transport",
                subcategory = null,
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("80"),
                warning = null,
                critical = null,
            )
        adapter.upsert(threshold, organizationId)

        verify(jpaRepository, never()).save(
            ThresholdEntity(organization = organizationEntity, category = "Transport", period = ThresholdPeriod.MONTHLY),
        )
        assertThat(existing.notice).isEqualByComparingTo(BigDecimal("80"))
    }

    @Test
    fun `should update existing threshold when found by category and subcategory`() {
        val existing =
            ThresholdEntity(
                organization = organizationEntity,
                category = "Lebensmittel",
                subcategory = "Supermarkt",
                period = ThresholdPeriod.MONTHLY,
                id = 7L,
            )
        whenever(
            jpaRepository.findByOrganizationIdAndCategoryAndSubcategoryAndPeriod(
                organizationId,
                "Lebensmittel",
                "Supermarkt",
                ThresholdPeriod.MONTHLY,
            ),
        ).thenReturn(existing)
        whenever(jpaRepository.save(existing)).thenReturn(existing)

        val threshold =
            Threshold(
                id = 7L,
                category = "Lebensmittel",
                subcategory = "Supermarkt",
                period = ThresholdPeriod.MONTHLY,
                notice = null,
                warning = BigDecimal("90"),
                critical = null,
            )
        adapter.upsert(threshold, organizationId)

        assertThat(existing.warning).isEqualByComparingTo(BigDecimal("90"))
    }

    @Test
    fun `should delegate deleteThreshold to repository`() {
        adapter.deleteByIdAndOrganizationId(3L, organizationId)

        verify(jpaRepository).deleteByIdAndOrganizationId(3L, organizationId)
    }
}
