package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CategoryJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var categoryRepo: CategoryJpaRepository

    @Test
    fun `should find all categories for given organization`() {
        categoryRepo.save(CategoryEntity(name = "Lebensmittel", parent = null, organization = organization))
        categoryRepo.save(CategoryEntity(name = "Wohnen", parent = null, organization = organization))
        categoryRepo.save(CategoryEntity(name = "Einnahmen", parent = null, organization = otherOrganization))

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
    fun `should store parent-child relationship`() {
        val root = categoryRepo.save(CategoryEntity(name = "Lebensmittel", parent = null, organization = organization))
        val child = categoryRepo.save(CategoryEntity(name = "Supermarkt", parent = root, organization = organization))

        val found = categoryRepo.findAllByOrganizationId(organizationId)

        assertThat(found).hasSize(2)
        val childNode = found.first { it.name == "Supermarkt" }
        assertThat(childNode.parent?.name).isEqualTo("Lebensmittel")
    }

    @Test
    fun `should find node by name and parent`() {
        val root = categoryRepo.save(CategoryEntity(name = "Lebensmittel", parent = null, organization = organization))
        categoryRepo.save(CategoryEntity(name = "Supermarkt", parent = root, organization = organization))

        val found = categoryRepo.findByNameAndParentIdAndOrganizationId("Supermarkt", root.id, organizationId)

        assertThat(found).isNotNull
        assertThat(found?.name).isEqualTo("Supermarkt")
    }

    @Test
    fun `should return null when node not found`() {
        val result = categoryRepo.findByNameAndParentIdAndOrganizationId("NonExistent", null, organizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should find root node when parentId is null`() {
        categoryRepo.save(CategoryEntity(name = "Transport", parent = null, organization = organization))

        val found = categoryRepo.findByNameAndParentIdAndOrganizationId("Transport", null, organizationId)

        assertThat(found).isNotNull
        assertThat(found?.name).isEqualTo("Transport")
    }
}
