package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CollectionTransactionJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var collectionRepo: CollectionJpaRepository
    @Autowired private lateinit var collectionTransactionRepo: CollectionTransactionJpaRepository

    private fun savedCollection(forUser: UserEntity = user) =
        collectionRepo.save(CollectionEntity(user = forUser, name = "Test Collection"))

    private fun savedLink(collectionId: Long, transactionId: Long) =
        collectionTransactionRepo.save(CollectionTransactionEntity(collectionId = collectionId, transactionId = transactionId))

    @Test
    fun `should find collection transaction links by transaction ids`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx3 = savedTransaction("fp-3")
        val tx1Id = checkNotNull(tx1.id)
        val tx3Id = checkNotNull(tx3.id)
        savedLink(collectionId, tx1Id)
        savedLink(collectionId, checkNotNull(tx2.id))

        val result = collectionTransactionRepo.findByTransactionIds(listOf(tx1Id, tx3Id))

        assertThat(result).hasSize(1)
        assertThat(result.first().transactionId).isEqualTo(tx1Id)
    }

    @Test
    fun `should find transaction ids by collection id`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx1Id = checkNotNull(tx1.id)
        val tx2Id = checkNotNull(tx2.id)
        savedLink(collectionId, tx1Id)
        savedLink(collectionId, tx2Id)

        val result = collectionTransactionRepo.findTransactionIdsByCollectionId(collectionId)

        assertThat(result).containsExactlyInAnyOrder(tx1Id, tx2Id)
    }

    @Test
    fun `should return empty list when collection has no transactions`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)

        val result = collectionTransactionRepo.findTransactionIdsByCollectionId(collectionId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should check existence of link between collection and transaction`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)
        val tx = savedTransaction("fp-1")
        val txId = checkNotNull(tx.id)
        savedLink(collectionId, txId)

        assertThat(collectionTransactionRepo.existsByCollectionIdAndTransactionId(collectionId, txId)).isTrue()
    }

    @Test
    fun `should return false when link does not exist`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)
        val tx = savedTransaction("fp-1")
        val txId = checkNotNull(tx.id)

        assertThat(collectionTransactionRepo.existsByCollectionIdAndTransactionId(collectionId, txId)).isFalse()
    }

    @Test
    fun `should delete link by collection id and transaction id`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)
        val tx = savedTransaction("fp-1")
        val txId = checkNotNull(tx.id)
        savedLink(collectionId, txId)
        flushAndClear()

        collectionTransactionRepo.deleteByCollectionIdAndTransactionId(collectionId, txId)
        flushAndClear()

        assertThat(collectionTransactionRepo.existsByCollectionIdAndTransactionId(collectionId, txId)).isFalse()
    }

    @Test
    fun `should only delete specified link and keep other links`() {
        val collection = savedCollection()
        val collectionId = checkNotNull(collection.id)
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx1Id = checkNotNull(tx1.id)
        val tx2Id = checkNotNull(tx2.id)
        savedLink(collectionId, tx1Id)
        savedLink(collectionId, tx2Id)
        flushAndClear()

        collectionTransactionRepo.deleteByCollectionIdAndTransactionId(collectionId, tx1Id)
        flushAndClear()

        assertThat(collectionTransactionRepo.existsByCollectionIdAndTransactionId(collectionId, tx2Id)).isTrue()
    }
}
