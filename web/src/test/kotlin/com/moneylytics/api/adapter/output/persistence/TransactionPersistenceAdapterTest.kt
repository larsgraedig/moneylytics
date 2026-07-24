package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CategoryUpdateEntry
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class TransactionPersistenceAdapterTest {
    private val jpaRepository: TransactionJpaRepository = mock()
    private val accountJpaRepository: AccountJpaRepository = mock()
    private val userJpaRepository: UserJpaRepository = mock()
    private val offsetJpaRepository: TransactionOffsetJpaRepository = mock()
    private val groupMemberJpaRepository: TransactionGroupMemberJpaRepository = mock()
    private val groupJpaRepository: TransactionGroupJpaRepository = mock()
    private val collectionTransactionJpaRepository: CollectionTransactionJpaRepository = mock()
    private val collectionJpaRepository: CollectionJpaRepository = mock()
    private val budgetTransactionJpaRepository: BudgetTransactionJpaRepository = mock()
    private val adapter =
        TransactionPersistenceAdapter(
            jpaRepository,
            accountJpaRepository,
            userJpaRepository,
            offsetJpaRepository,
            groupMemberJpaRepository,
            groupJpaRepository,
            collectionTransactionJpaRepository,
            collectionJpaRepository,
            budgetTransactionJpaRepository,
        )

    private val userId = 1L
    private val date = LocalDate.of(2025, 1, 15)
    private val userEntity = UserEntity(externalId = "test@test.de", id = userId)
    private val accountEntity = AccountEntity(iban = "DE00TEST", name = "Test", user = userEntity, id = 10L)

    @Test
    fun `should return 0 from saveAll when list is empty`() {
        val result = adapter.saveAll(emptyList(), userId)

        assertThat(result).isEqualTo(0)
        verify(jpaRepository, never()).saveAll(any<List<TransactionEntity>>())
    }

    @Test
    fun `should skip transactions with existing fingerprints in saveAll`() {
        val tx1 = domainTx(amount = BigDecimal("-100"))
        val tx2 = domainTx(amount = BigDecimal("-200"))
        whenever(accountJpaRepository.findByIbanAndUserId("DE00TEST", userId)).thenReturn(accountEntity)
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)
        val fp1 = computeFingerprint(tx1)
        val fp2 = computeFingerprint(tx2)
        whenever(jpaRepository.findExistingFingerprints(any(), any())).thenReturn(listOf(fp1))
        whenever(jpaRepository.saveAll(any<List<TransactionEntity>>())).thenReturn(emptyList())

        val result = adapter.saveAll(listOf(tx1, tx2), userId)

        assertThat(result).isEqualTo(1)
        val captor = argumentCaptor<List<TransactionEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(1)
    }

    @Test
    fun `should assign different fingerprints to duplicate transactions in same batch`() {
        val tx = domainTx()
        val transactions = listOf(tx, tx)
        whenever(accountJpaRepository.findByIbanAndUserId("DE00TEST", userId)).thenReturn(accountEntity)
        whenever(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity)
        whenever(jpaRepository.findExistingFingerprints(any(), any())).thenReturn(emptyList())
        whenever(jpaRepository.saveAll(any<List<TransactionEntity>>())).thenReturn(emptyList())

        val result = adapter.saveAll(transactions, userId)

        assertThat(result).isEqualTo(2)
        val captor = argumentCaptor<List<TransactionEntity>>()
        verify(jpaRepository).saveAll(captor.capture())
        val fingerprints = captor.firstValue.map { it.fingerprint }
        assertThat(fingerprints[0]).isNotEqualTo(fingerprints[1])
    }

    @Test
    fun `should not overwrite existing non-null fields in enrichByFingerprint`() {
        val entity = txEntity(1L, purpose = "existing-purpose", counterpartyName = "existing-name")
        whenever(jpaRepository.findByFingerprintAndUserId("fp1", userId)).thenReturn(entity)

        adapter.enrichByFingerprint("fp1", userId, purpose = "new-purpose", counterpartyName = "new-name", counterpartyIban = null)

        assertThat(entity.purpose).isEqualTo("existing-purpose")
        assertThat(entity.counterpartyName).isEqualTo("existing-name")
    }

    @Test
    fun `should update null fields in enrichByFingerprint`() {
        val entity = txEntity(1L, purpose = null, counterpartyName = null)
        whenever(jpaRepository.findByFingerprintAndUserId("fp1", userId)).thenReturn(entity)
        whenever(jpaRepository.save(entity)).thenReturn(entity)

        adapter.enrichByFingerprint("fp1", userId, purpose = "Gehalt", counterpartyName = "Arbeitgeber", counterpartyIban = null)

        assertThat(entity.purpose).isEqualTo("Gehalt")
        assertThat(entity.counterpartyName).isEqualTo("Arbeitgeber")
    }

    @Test
    fun `should skip enrichByFingerprint when entity not found`() {
        whenever(jpaRepository.findByFingerprintAndUserId("unknown", userId)).thenReturn(null)

        adapter.enrichByFingerprint("unknown", userId, purpose = "Test", counterpartyName = null, counterpartyIban = null)

        verify(jpaRepository, never()).save(any())
    }

    @Test
    fun `should return null from updateCategory when entity not found`() {
        whenever(jpaRepository.findByIdAndUserId(99L, userId)).thenReturn(null)

        val result = adapter.updateCategory(99L, userId, "Lebensmittel", "Supermarkt", null)

        assertThat(result).isNull()
    }

    @Test
    fun `should convert blank category strings to null on updateCategory`() {
        val entity = txEntity(1L)
        val savedEntity = txEntity(1L)
        whenever(jpaRepository.findByIdAndUserId(1L, userId)).thenReturn(entity)
        whenever(jpaRepository.save(entity)).thenReturn(savedEntity)
        stubEnrichEmpty(listOf(1L))

        adapter.updateCategory(1L, userId, "  ", "  ", "  ")

        assertThat(entity.category).isNull()
        assertThat(entity.subcategory).isNull()
        assertThat(entity.categoryGroup).isNull()
    }

    @Test
    fun `should return null from updateComment when entity not found`() {
        whenever(jpaRepository.findByIdAndUserId(99L, userId)).thenReturn(null)

        val result = adapter.updateComment(99L, userId, "test")

        assertThat(result).isNull()
    }

    @Test
    fun `should convert blank comment to null on updateComment`() {
        val entity = txEntity(1L)
        whenever(jpaRepository.findByIdAndUserId(1L, userId)).thenReturn(entity)
        whenever(jpaRepository.save(entity)).thenReturn(txEntity(1L))
        stubEnrichEmpty(listOf(1L))

        adapter.updateComment(1L, userId, "  ")

        assertThat(entity.comment).isNull()
    }

    @Test
    fun `should return empty list from bulkUpdateCategory when updates is empty`() {
        val result = adapter.bulkUpdateCategory(emptyList(), userId)

        assertThat(result).isEmpty()
        verify(jpaRepository, never()).findByIdsAndUserId(any(), any())
    }

    @Test
    fun `should apply bulk category updates`() {
        val entity = txEntity(1L)
        whenever(jpaRepository.findByIdsAndUserId(setOf(1L), userId)).thenReturn(listOf(entity))
        whenever(jpaRepository.saveAll(any<List<TransactionEntity>>())).thenReturn(listOf(entity))
        stubEnrichEmpty(listOf(1L))

        adapter.bulkUpdateCategory(
            listOf(CategoryUpdateEntry(id = 1L, category = "Transport", subcategory = "ÖPNV", categoryGroup = null)),
            userId,
        )

        assertThat(entity.category).isEqualTo("Transport")
        assertThat(entity.subcategory).isEqualTo("ÖPNV")
    }

    @Test
    fun `should use account-specific query when IBAN provided in findByAccountingDateBetween`() {
        stubEnrichEmpty(emptyList())
        whenever(jpaRepository.findByUserIdAndAccountIbanAndAccountingDateBetween(userId, "DE00TEST", date, date))
            .thenReturn(emptyList())

        adapter.findByAccountingDateBetween(date, date, userId, accountIban = "DE00TEST")

        verify(jpaRepository).findByUserIdAndAccountIbanAndAccountingDateBetween(userId, "DE00TEST", date, date)
        verify(jpaRepository, never()).findByUserIdAndAccountingDateBetween(any(), any(), any())
    }

    @Test
    fun `should use general query when no IBAN in findByAccountingDateBetween`() {
        stubEnrichEmpty(emptyList())
        whenever(jpaRepository.findByUserIdAndAccountingDateBetween(userId, date, date)).thenReturn(emptyList())

        adapter.findByAccountingDateBetween(date, date, userId, accountIban = null)

        verify(jpaRepository).findByUserIdAndAccountingDateBetween(userId, date, date)
        verify(jpaRepository, never()).findByUserIdAndAccountIbanAndAccountingDateBetween(any(), any(), any(), any())
    }

    @Test
    fun `should map offset link from A-side perspective`() {
        val txA = txEntity(1L, amount = BigDecimal("-100"))
        val txB = txEntity(2L, amount = BigDecimal("50"))
        val offsetEntity =
            TransactionOffsetEntity(
                transactionA = txA,
                transactionB = txB,
                amountA = BigDecimal("-80"),
                amountB = BigDecimal("50"),
                groupId = 10L,
                id = 5L,
            )
        whenever(jpaRepository.findByUserIdAndAccountingDateBetween(userId, date, date)).thenReturn(listOf(txA))
        whenever(offsetJpaRepository.findByTransactionIds(listOf(1L))).thenReturn(listOf(offsetEntity))
        stubGroupsAndCollectionsEmpty(listOf(1L))

        val result = adapter.findByAccountingDateBetween(date, date, userId, null)

        val link = result[0].offsetLinks[0]
        assertThat(link.linkedTransactionId).isEqualTo(2L)
        assertThat(link.linkedTransactionAmount).isEqualByComparingTo(BigDecimal("50"))
        assertThat(link.myCommitted).isEqualByComparingTo(BigDecimal("-80"))
        assertThat(link.groupId).isEqualTo(10L)
    }

    @Test
    fun `should map offset link from B-side perspective`() {
        val txA = txEntity(1L, amount = BigDecimal("-100"))
        val txB = txEntity(2L, amount = BigDecimal("50"))
        val offsetEntity =
            TransactionOffsetEntity(
                transactionA = txA,
                transactionB = txB,
                amountA = BigDecimal("-80"),
                amountB = BigDecimal("50"),
                groupId = 10L,
                id = 5L,
            )
        whenever(jpaRepository.findByUserIdAndAccountingDateBetween(userId, date, date)).thenReturn(listOf(txB))
        whenever(offsetJpaRepository.findByTransactionIds(listOf(2L))).thenReturn(listOf(offsetEntity))
        stubGroupsAndCollectionsEmpty(listOf(2L))

        val result = adapter.findByAccountingDateBetween(date, date, userId, null)

        val link = result[0].offsetLinks[0]
        assertThat(link.linkedTransactionId).isEqualTo(1L)
        assertThat(link.linkedTransactionAmount).isEqualByComparingTo(BigDecimal("-100"))
        assertThat(link.myCommitted).isEqualByComparingTo(BigDecimal("50"))
    }

    @Test
    fun `should fall back to transaction amount as myCommitted when amountA is null on A-side`() {
        val txA = txEntity(1L, amount = BigDecimal("-100"))
        val txB = txEntity(2L, amount = BigDecimal("50"))
        val offsetEntity =
            TransactionOffsetEntity(transactionA = txA, transactionB = txB, amountA = null, amountB = null, groupId = null, id = 5L)
        whenever(jpaRepository.findByUserIdAndAccountingDateBetween(userId, date, date)).thenReturn(listOf(txA))
        whenever(offsetJpaRepository.findByTransactionIds(listOf(1L))).thenReturn(listOf(offsetEntity))
        stubGroupsAndCollectionsEmpty(listOf(1L))

        val result = adapter.findByAccountingDateBetween(date, date, userId, null)

        assertThat(result[0].offsetLinks[0].myCommitted).isEqualByComparingTo(BigDecimal("-100"))
    }

    @Test
    fun `should map result rows to LocalDate map in latestTransactionDatesByUserId`() {
        val latestDate = LocalDate.of(2025, 6, 15)
        whenever(jpaRepository.findLatestDatePerIban(userId)).thenReturn(listOf(arrayOf("DE00TEST", latestDate)))

        val result = adapter.latestTransactionDatesByUserId(userId)

        assertThat(result).containsEntry("DE00TEST", latestDate)
    }

    @Test
    fun `should return empty map when no transactions for user`() {
        whenever(jpaRepository.findLatestDatePerIban(userId)).thenReturn(emptyList())

        val result = adapter.latestTransactionDatesByUserId(userId)

        assertThat(result).isEmpty()
    }

    private fun stubEnrichEmpty(ids: List<Long>) {
        whenever(offsetJpaRepository.findByTransactionIds(ids)).thenReturn(emptyList())
        stubGroupsAndCollectionsEmpty(ids)
    }

    private fun stubGroupsAndCollectionsEmpty(ids: List<Long>) {
        whenever(groupMemberJpaRepository.findByTransactionIds(ids)).thenReturn(emptyList())
        whenever(collectionTransactionJpaRepository.findByTransactionIds(ids)).thenReturn(emptyList())
        whenever(budgetTransactionJpaRepository.findByTransactionIds(ids)).thenReturn(emptyList())
    }

    private fun txEntity(
        id: Long,
        amount: BigDecimal = BigDecimal("-100"),
        purpose: String? = null,
        counterpartyName: String? = null,
    ) = TransactionEntity(
        id = id,
        category = null,
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        account = accountEntity,
        fingerprint = "fp$id",
        user = userEntity,
        purpose = purpose,
        counterpartyName = counterpartyName,
    )

    private fun domainTx(amount: BigDecimal = BigDecimal("-100")) =
        Transaction(
            category = null,
            subcategory = null,
            bookingDate = date,
            valueDate = date,
            accountingDate = date,
            amount = amount,
            currency = "EUR",
            accountIban = "DE00TEST",
        )

    private fun computeFingerprint(tx: Transaction): String {
        val raw = "${tx.accountIban}|${tx.bookingDate}|${tx.valueDate}|${tx.amount.stripTrailingZeros().toPlainString()}|${tx.currency}"
        return java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
