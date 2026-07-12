package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CollectionJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var collectionRepo: CollectionJpaRepository

    private fun savedCollection(
        name: String = "Sommer 2025",
        forUser: UserEntity = user,
    ) = collectionRepo.save(CollectionEntity(user = forUser, name = name))

    @Test
    fun `should find all collections for user`() {
        savedCollection(name = "Sommer 2025")
        savedCollection(name = "Haushalt Q1")
        savedCollection(name = "Fremde Sammlung", forUser = otherUser)

        val result = collectionRepo.findByUserId(user.id!!)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("Sommer 2025", "Haushalt Q1")
    }

    @Test
    fun `should return empty list when user has no collections`() {
        val result = collectionRepo.findByUserId(otherUser.id!!)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should find collection by id and user id`() {
        val collection = savedCollection()

        val result = collectionRepo.findByIdAndUserId(collection.id!!, user.id!!)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("Sommer 2025")
    }

    @Test
    fun `should return null for collection belonging to other user`() {
        val otherCollection = savedCollection(forUser = otherUser)

        val result = collectionRepo.findByIdAndUserId(otherCollection.id!!, user.id!!)

        assertThat(result).isNull()
    }

    @Test
    fun `should delete collection by id and user id`() {
        val collection = savedCollection()
        flushAndClear()

        collectionRepo.deleteByIdAndUserId(collection.id!!, user.id!!)
        flushAndClear()

        assertThat(collectionRepo.findById(collection.id!!)).isEmpty
    }

    @Test
    fun `should not delete collection belonging to other user`() {
        val otherCollection = savedCollection(forUser = otherUser)
        flushAndClear()

        collectionRepo.deleteByIdAndUserId(otherCollection.id!!, user.id!!)
        flushAndClear()

        assertThat(collectionRepo.findById(otherCollection.id!!)).isPresent
    }
}
