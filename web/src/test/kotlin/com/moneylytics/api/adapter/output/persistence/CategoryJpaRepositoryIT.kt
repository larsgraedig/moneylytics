package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CategoryJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var categoryRepo: CategoryJpaRepository

    @Test
    fun `should find all categories for given organization`() {
        categoryRepo.save(CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", groupName = null, organization = organization))
        categoryRepo.save(CategoryEntity(name = "Wohnen", subcategory = "Miete", groupName = null, organization = organization))
        categoryRepo.save(CategoryEntity(name = "Einnahmen", subcategory = "Gehalt", groupName = null, organization = otherOrganization))

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
    fun `should store and return subcategory and group`() {
        categoryRepo.save(
            CategoryEntity(name = "Lebensmittel", subcategory = "Supermarkt", groupName = "Konsum", organization = organization),
        )

        val result = categoryRepo.findAllByOrganizationId(organizationId)

        assertThat(result.first().subcategory).isEqualTo("Supermarkt")
        assertThat(result.first().groupName).isEqualTo("Konsum")
    }
}
