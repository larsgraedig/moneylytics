package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CollectionTransactionJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var collectionRepo: CollectionJpaRepository

    @Autowired private lateinit var collectionTransactionRepo: CollectionTransactionJpaRepository

    private fun savedCollection(forUser: UserEntity = user) =
        collectionRepo.save(CollectionEntity(user = forUser, name = "Test Collection"))

    private fun savedLink(
        collectionId: Long,
        transactionId: Long,
    ) = collectionTransactionRepo.save(CollectionTransactionEntity(collectionId = collectionId, transactionId = transactionId))

    @Test
    fun `should find collection transaction links by transaction ids`() {
        val collection = savedCollection()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx3 = savedTransaction("fp-3")
        savedLink(collection.id!!, tx1.id!!)
        savedLink(collection.id!!, tx2.id!!)

        val result = collectionTransactionRepo.findByTransactionIds(listOf(tx1.id!!, tx3.id!!))

        assertThat(result).hasSize(1)
        assertThat(result.first().transactionId).isEqualTo(tx1.id)
    }

    @Test
    fun `should find transaction ids by collection id`() {
        val collection = savedCollection()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedLink(collection.id!!, tx1.id!!)
        savedLink(collection.id!!, tx2.id!!)

        val result = collectionTransactionRepo.findTransactionIdsByCollectionId(collection.id!!)

        assertThat(result).containsExactlyInAnyOrder(tx1.id, tx2.id)
    }

    @Test
    fun `should return empty list when collection has no transactions`() {
        val collection = savedCollection()

        val result = collectionTransactionRepo.findTransactionIdsByCollectionId(collection.id!!)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should check existence of link between collection and transaction`() {
        val collection = savedCollection()
        val tx = savedTransaction("fp-1")
        savedLink(collection.id!!, tx.id!!)

        assertThat(collectionTransactionRepo.existsByCollectionIdAndTransactionId(collection.id!!, tx.id!!)).isTrue()
    }

    @Test
    fun `should return false when link does not exist`() {
        val collection = savedCollection()
        val tx = savedTransaction("fp-1")

        assertThat(collectionTransactionRepo.existsByCollectionIdAndTransactionId(collection.id!!, tx.id!!)).isFalse()
    }

    @Test
    fun `should delete link by collection id and transaction id`() {
        val collection = savedCollection()
        val tx = savedTransaction("fp-1")
        savedLink(collection.id!!, tx.id!!)
        flushAndClear()

        collectionTransactionRepo.deleteByCollectionIdAndTransactionId(collection.id!!, tx.id!!)
        flushAndClear()

        assertThat(collectionTransactionRepo.existsByCollectionIdAndTransactionId(collection.id!!, tx.id!!)).isFalse()
    }

    @Test
    fun `should only delete specified link and keep other links`() {
        val collection = savedCollection()
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        savedLink(collection.id!!, tx1.id!!)
        savedLink(collection.id!!, tx2.id!!)
        flushAndClear()

        collectionTransactionRepo.deleteByCollectionIdAndTransactionId(collection.id!!, tx1.id!!)
        flushAndClear()

        assertThat(collectionTransactionRepo.existsByCollectionIdAndTransactionId(collection.id!!, tx2.id!!)).isTrue()
    }
}
