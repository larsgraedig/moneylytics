package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CollectionJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var collectionRepo: CollectionJpaRepository

    private fun savedCollection(
        name: String = "Sommer 2025",
        forOrganization: OrganizationEntity = organization,
    ) = collectionRepo.save(CollectionEntity(organization = forOrganization, name = name))

    @Test
    fun `should find all collections for user`() {
        savedCollection(name = "Sommer 2025")
        savedCollection(name = "Haushalt Q1")
        savedCollection(name = "Fremde Sammlung", forOrganization = otherOrganization)

        val result = collectionRepo.findByOrganizationId(organizationId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("Sommer 2025", "Haushalt Q1")
    }

    @Test
    fun `should return empty list when user has no collections`() {
        val result = collectionRepo.findByOrganizationId(otherOrganizationId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should find collection by id and user id`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)

        val result = collectionRepo.findByIdAndOrganizationId(collectionId, organizationId)

        assertThat(result).isNotNull
        assertThat(result?.name).isEqualTo("Sommer 2025")
    }

    @Test
    fun `should return null for collection belonging to other user`() {
        val otherCollection = savedCollection(forOrganization = otherOrganization)
        val otherCollectionId = checkNotNull(otherCollection.id)

        val result = collectionRepo.findByIdAndOrganizationId(otherCollectionId, organizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should delete collection by id and user id`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)
        flushAndClear()

        collectionRepo.deleteByIdAndOrganizationId(collectionId, organizationId)
        flushAndClear()

        assertThat(collectionRepo.findById(collectionId)).isEmpty
    }

    @Test
    fun `should not delete collection belonging to other user`() {
        val otherCollection = savedCollection(forOrganization = otherOrganization)
        val otherCollectionId = checkNotNull(otherCollection.id)
        flushAndClear()

        collectionRepo.deleteByIdAndOrganizationId(otherCollectionId, organizationId)
        flushAndClear()

        assertThat(collectionRepo.findById(otherCollectionId)).isPresent
    }
}
