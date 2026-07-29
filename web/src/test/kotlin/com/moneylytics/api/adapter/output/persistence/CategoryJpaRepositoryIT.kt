package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CategoryJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var categoryRepo: CategoryJpaRepository

    @Test
    fun `should find all categories for given organization`() {
        categoryRepo.save(CategoryEntity(name = "Lebensmittel", subcategory = null, groupName = "Supermarkt", organization = organization))
        categoryRepo.save(CategoryEntity(name = "Wohnen", subcategory = null, groupName = "Miete", organization = organization))
        categoryRepo.save(CategoryEntity(name = "Einnahmen", subcategory = null, groupName = "Gehalt", organization = otherOrganization))

        val result = categoryRepo.findAllByOrganizationId(organizationId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("Lebensmittel", "Wohnen")
    }

    @Test
    fun `should return empty list when organization has no categories`() {
        val result = categoryRepo.findAllByOrganizationId(otherOrganizationId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should store and return subcategory`() {
        categoryRepo.save(
            CategoryEntity(name = "Lebensmittel", subcategory = "Konsum", groupName = "Supermarkt", organization = organization),
        )

        val result = categoryRepo.findAllByOrganizationId(organizationId)

        assertThat(result.first().subcategory).isEqualTo("Konsum")
        assertThat(result.first().groupName).isEqualTo("Supermarkt")
    }
}
