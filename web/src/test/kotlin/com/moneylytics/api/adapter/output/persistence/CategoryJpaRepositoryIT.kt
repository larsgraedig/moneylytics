package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CategoryJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var categoryRepo: CategoryJpaRepository

    @Test
    fun `should find all categories for given user`() {
        categoryRepo.save(CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", user = user))
        categoryRepo.save(CategoryEntity(name = "Wohnen", subcategory = "Miete", user = user))
        categoryRepo.save(CategoryEntity(name = "Einnahmen", subcategory = "Gehalt", user = otherUser))

        val result = categoryRepo.findAllByUserId(userId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("Lebensmittel", "Wohnen")
    }

    @Test
    fun `should return empty list when user has no categories`() {
        val result = categoryRepo.findAllByUserId(otherUserId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should store and return category group`() {
        categoryRepo.save(CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", user = user, categoryGroup = "Konsum"))

        val result = categoryRepo.findAllByUserId(userId)

        assertThat(result.first().categoryGroup).isEqualTo("Konsum")
    }
}
