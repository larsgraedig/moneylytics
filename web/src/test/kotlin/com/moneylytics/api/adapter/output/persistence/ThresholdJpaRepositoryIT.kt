package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.ThresholdPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class ThresholdJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var thresholdRepo: ThresholdJpaRepository

    private fun savedThreshold(
        category: String = "Lebensmittel",
        subcategory: String? = null,
        period: ThresholdPeriod = ThresholdPeriod.MONTHLY,
        forUser: UserEntity = user,
    ) = thresholdRepo.save(
        ThresholdEntity(
            user = forUser,
            category = category,
            subcategory = subcategory,
            period = period,
            notice = BigDecimal("300"),
        ),
    )

    @Test
    fun `should find all thresholds for user`() {
        savedThreshold(category = "Lebensmittel")
        savedThreshold(category = "Transport")
        savedThreshold(category = "Fremdes", forUser = otherUser)

        val result = thresholdRepo.findByUserId(user.id!!)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.category }).containsExactlyInAnyOrder("Lebensmittel", "Transport")
    }

    @Test
    fun `should return empty list when user has no thresholds`() {
        val result = thresholdRepo.findByUserId(otherUser.id!!)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should find threshold for category without subcategory`() {
        savedThreshold(category = "Lebensmittel", subcategory = null, period = ThresholdPeriod.MONTHLY)
        savedThreshold(category = "Lebensmittel", subcategory = "Restaurant", period = ThresholdPeriod.MONTHLY)

        val result =
            thresholdRepo.findByUserIdAndCategoryAndSubcategoryIsNullAndPeriod(
                userId = user.id!!,
                category = "Lebensmittel",
                period = ThresholdPeriod.MONTHLY,
            )

        assertThat(result).isNotNull
        assertThat(result!!.subcategory).isNull()
    }

    @Test
    fun `should return null when threshold with null subcategory does not exist for period`() {
        savedThreshold(category = "Lebensmittel", subcategory = null, period = ThresholdPeriod.MONTHLY)

        val result =
            thresholdRepo.findByUserIdAndCategoryAndSubcategoryIsNullAndPeriod(
                userId = user.id!!,
                category = "Lebensmittel",
                period = ThresholdPeriod.YEARLY,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `should find threshold for category with specific subcategory`() {
        savedThreshold(category = "Lebensmittel", subcategory = null)
        savedThreshold(category = "Lebensmittel", subcategory = "Restaurant")

        val result =
            thresholdRepo.findByUserIdAndCategoryAndSubcategoryAndPeriod(
                userId = user.id!!,
                category = "Lebensmittel",
                subcategory = "Restaurant",
                period = ThresholdPeriod.MONTHLY,
            )

        assertThat(result).isNotNull
        assertThat(result!!.subcategory).isEqualTo("Restaurant")
    }

    @Test
    fun `should delete threshold by id and user id`() {
        val threshold = savedThreshold()
        flushAndClear()

        thresholdRepo.deleteByIdAndUserId(threshold.id!!, user.id!!)
        flushAndClear()

        assertThat(thresholdRepo.findById(threshold.id!!)).isEmpty
    }

    @Test
    fun `should not delete threshold belonging to other user`() {
        val otherThreshold = savedThreshold(forUser = otherUser)
        flushAndClear()

        thresholdRepo.deleteByIdAndUserId(otherThreshold.id!!, user.id!!)
        flushAndClear()

        assertThat(thresholdRepo.findById(otherThreshold.id!!)).isPresent
    }
}
