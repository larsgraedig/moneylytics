package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.ThresholdPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class ThresholdJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var thresholdRepo: ThresholdJpaRepository

    @Autowired private lateinit var categoryRepo: CategoryJpaRepository

    private fun savedCategory(
        name: String,
        parent: CategoryEntity? = null,
        forOrganization: OrganizationEntity = organization,
    ): CategoryEntity = categoryRepo.save(CategoryEntity(name = name, parent = parent, organization = forOrganization))

    private fun savedThreshold(
        categoryEntity: CategoryEntity,
        period: ThresholdPeriod = ThresholdPeriod.MONTHLY,
        forOrganization: OrganizationEntity = organization,
    ) = thresholdRepo.save(
        ThresholdEntity(
            organization = forOrganization,
            categoryEntity = categoryEntity,
            period = period,
            notice = BigDecimal("300"),
        ),
    )

    @Test
    fun `should find all thresholds with category_id for organization`() {
        val cat1 = savedCategory("Lebensmittel")
        val cat2 = savedCategory("Transport")
        val otherCat = savedCategory("Fremdes", forOrganization = otherOrganization)
        savedThreshold(cat1)
        savedThreshold(cat2)
        savedThreshold(otherCat, forOrganization = otherOrganization)

        val result = thresholdRepo.findByOrganizationIdAndCategoryEntityIsNotNull(organizationId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.categoryEntity?.name }).containsExactlyInAnyOrder("Lebensmittel", "Transport")
    }

    @Test
    fun `should return empty list when organization has no thresholds`() {
        val result = thresholdRepo.findByOrganizationIdAndCategoryEntityIsNotNull(otherOrganizationId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should find threshold by categoryId and period`() {
        val cat = savedCategory("Lebensmittel")
        savedThreshold(cat, period = ThresholdPeriod.MONTHLY)

        val result =
            thresholdRepo.findByOrganizationIdAndCategoryEntityIdAndPeriod(
                organizationId = organizationId,
                categoryEntityId = checkNotNull(cat.id),
                period = ThresholdPeriod.MONTHLY,
            )

        assertThat(result).isNotNull
        assertThat(result?.categoryEntity?.name).isEqualTo("Lebensmittel")
    }

    @Test
    fun `should return null when no threshold exists for categoryId and period`() {
        val cat = savedCategory("Lebensmittel")
        savedThreshold(cat, period = ThresholdPeriod.MONTHLY)

        val result =
            thresholdRepo.findByOrganizationIdAndCategoryEntityIdAndPeriod(
                organizationId = organizationId,
                categoryEntityId = checkNotNull(cat.id),
                period = ThresholdPeriod.YEARLY,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `should delete threshold by id and organization id`() {
        val cat = savedCategory("Lebensmittel")
        val threshold = savedThreshold(cat)
        val thresholdId = checkNotNull(threshold.id)
        flushAndClear()

        thresholdRepo.deleteByIdAndOrganizationId(thresholdId, organizationId)
        flushAndClear()

        assertThat(thresholdRepo.findById(thresholdId)).isEmpty
    }

    @Test
    fun `should not delete threshold belonging to other organization`() {
        val otherCat = savedCategory("Fremdes", forOrganization = otherOrganization)
        val otherThreshold = savedThreshold(otherCat, forOrganization = otherOrganization)
        val otherThresholdId = checkNotNull(otherThreshold.id)
        flushAndClear()

        thresholdRepo.deleteByIdAndOrganizationId(otherThresholdId, organizationId)
        flushAndClear()

        assertThat(thresholdRepo.findById(otherThresholdId)).isPresent
    }
}
