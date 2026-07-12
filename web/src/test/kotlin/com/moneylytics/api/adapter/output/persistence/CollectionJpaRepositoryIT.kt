package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CollectionJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var collectionRepo: CollectionJpaRepository

    private fun savedCollection(name: String = "Sommer 2025", forUser: UserEntity = user) =
        collectionRepo.save(CollectionEntity(user = forUser, name = name))

    @Test
    fun `should find all collections for user`() {
        savedCollection(name = "Sommer 2025")
        savedCollection(name = "Haushalt Q1")
        savedCollection(name = "Fremde Sammlung", forUser = otherUser)

        val result = collectionRepo.findByUserId(userId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("Sommer 2025", "Haushalt Q1")
    }

    @Test
    fun `should return empty list when user has no collections`() {
        val result = collectionRepo.findByUserId(otherUserId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should find collection by id and user id`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)

        val result = collectionRepo.findByIdAndUserId(collectionId, userId)

        assertThat(result).isNotNull
        assertThat(result?.name).isEqualTo("Sommer 2025")
    }

    @Test
    fun `should return null for collection belonging to other user`() {
        val otherCollection = savedCollection(forUser = otherUser)
        val otherCollectionId = checkNotNull(otherCollection.id)

        val result = collectionRepo.findByIdAndUserId(otherCollectionId, userId)

        assertThat(result).isNull()
    }

    @Test
    fun `should delete collection by id and user id`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)
        flushAndClear()

        collectionRepo.deleteByIdAndUserId(collectionId, userId)
        flushAndClear()

        assertThat(collectionRepo.findById(collectionId)).isEmpty
    }

    @Test
    fun `should not delete collection belonging to other user`() {
        val otherCollection = savedCollection(forUser = otherUser)
        val otherCollectionId = checkNotNull(otherCollection.id)
        flushAndClear()

        collectionRepo.deleteByIdAndUserId(otherCollectionId, userId)
        flushAndClear()

        assertThat(collectionRepo.findById(otherCollectionId)).isPresent
    }
}
