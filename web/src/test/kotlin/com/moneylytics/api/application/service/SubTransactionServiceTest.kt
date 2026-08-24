package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.CreateVirtualTransactionCommand
import com.moneylytics.api.application.port.input.MergeTransactionsCommand
import com.moneylytics.api.application.port.input.SplitTransactionCommand
import com.moneylytics.api.application.port.output.SubTransactionPort
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.SplitItem
import com.moneylytics.api.domain.Transaction
import com.moneylytics.api.domain.TransactionOffsetLink
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class SubTransactionServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val subTransactionPort: SubTransactionPort = mock()
    private val service = SubTransactionService(transactionRepository, subTransactionPort)

    private val orgId = 1L
    private val date = LocalDate.of(2025, 3, 1)
    private val iban = "DE00TEST000000000001"

    private fun physicalTx(
        id: Long,
        amount: BigDecimal = BigDecimal("-100"),
        excluded: Boolean = false,
        parentId: Long? = null,
    ) = Transaction(
        id = id,
        category = null,
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = iban,
        excluded = excluded,
        parentId = parentId,
    )

    private fun virtualTx(
        id: Long,
        amount: BigDecimal,
        parentId: Long? = null,
    ) = Transaction(
        id = id,
        category = null,
        subcategory = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = iban,
        isVirtual = true,
        parentId = parentId,
    )

    @Test
    fun `splitTransaction should create children and exclude parent`() {
        val tx = physicalTx(1L, amount = BigDecimal("-100"))
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(tx)
        val child1 = virtualTx(2L, BigDecimal("-60"), parentId = 1L)
        val child2 = virtualTx(3L, BigDecimal("-40"), parentId = 1L)
        whenever(subTransactionPort.createVirtualChildren(any(), any(), eq(orgId))).thenReturn(listOf(child1, child2))
        val excludedTx = tx.copy(excluded = true)
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId))
            .thenReturn(tx)
            .thenReturn(excludedTx)

        val result =
            service.splitTransaction(
                SplitTransactionCommand(
                    transactionId = 1L,
                    splits =
                        listOf(
                            SplitItem(BigDecimal("-60"), null, null),
                            SplitItem(BigDecimal("-40"), null, null),
                        ),
                    organizationId = orgId,
                ),
            )

        verify(subTransactionPort).setExcluded(1L, orgId, true)
        assertThat(result.parent.id).isEqualTo(1L)
        assertThat(result.children).hasSize(2)
    }

    @Test
    fun `splitTransaction should fail when sum of splits does not equal parent amount`() {
        val tx = physicalTx(1L, amount = BigDecimal("-100"))
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(tx)

        assertThatThrownBy {
            service.splitTransaction(
                SplitTransactionCommand(
                    transactionId = 1L,
                    splits =
                        listOf(
                            SplitItem(BigDecimal("-60"), null, null),
                            SplitItem(BigDecimal("-30"), null, null),
                        ),
                    organizationId = orgId,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Sum of split amounts")
    }

    @Test
    fun `splitTransaction should fail when fewer than 2 splits`() {
        val tx = physicalTx(1L, amount = BigDecimal("-100"))
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(tx)

        assertThatThrownBy {
            service.splitTransaction(
                SplitTransactionCommand(
                    transactionId = 1L,
                    splits = listOf(SplitItem(BigDecimal("-100"), null, null)),
                    organizationId = orgId,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `splitTransaction should fail when transaction already has a parent`() {
        val tx = physicalTx(1L, parentId = 99L)
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(tx)

        assertThatThrownBy {
            service.splitTransaction(
                SplitTransactionCommand(
                    transactionId = 1L,
                    splits =
                        listOf(
                            SplitItem(BigDecimal("-50"), null, null),
                            SplitItem(BigDecimal("-50"), null, null),
                        ),
                    organizationId = orgId,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("child transaction")
    }

    @Test
    fun `splitTransaction should fail when transaction has offset links`() {
        val offsetLink =
            TransactionOffsetLink(
                id = 1L,
                linkedTransactionId = 2L,
                linkedTransactionAmount = BigDecimal("50"),
                amountA = null,
                amountB = null,
                myCommitted = BigDecimal("-100"),
                groupId = null,
            )
        val tx = physicalTx(1L, amount = BigDecimal("-100")).copy(offsetLinks = listOf(offsetLink))
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(tx)

        assertThatThrownBy {
            service.splitTransaction(
                SplitTransactionCommand(
                    transactionId = 1L,
                    splits =
                        listOf(
                            SplitItem(BigDecimal("-50"), null, null),
                            SplitItem(BigDecimal("-50"), null, null),
                        ),
                    organizationId = orgId,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("offset links")
    }

    @Test
    fun `unsplitTransaction should delete children and restore parent`() {
        val parent = physicalTx(1L, excluded = true)
        val child1 = virtualTx(2L, BigDecimal("-60"), parentId = 1L)
        val child2 = virtualTx(3L, BigDecimal("-40"), parentId = 1L)
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(parent)
        whenever(subTransactionPort.findChildren(1L, orgId)).thenReturn(listOf(child1, child2))
        whenever(transactionRepository.findByIdAndOrganizationId(2L, orgId)).thenReturn(child1)
        whenever(transactionRepository.findByIdAndOrganizationId(3L, orgId)).thenReturn(child2)

        service.unsplitTransaction(1L, orgId)

        verify(subTransactionPort).deleteVirtual(2L, orgId)
        verify(subTransactionPort).deleteVirtual(3L, orgId)
        verify(subTransactionPort).setExcluded(1L, orgId, false)
    }

    @Test
    fun `mergeTransactions should create virtual parent and exclude physicals`() {
        val tx1 = physicalTx(1L, BigDecimal("-30"))
        val tx2 = physicalTx(2L, BigDecimal("-70"))
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(tx1)
        whenever(transactionRepository.findByIdAndOrganizationId(2L, orgId)).thenReturn(tx2)
        val virtualParent = virtualTx(99L, BigDecimal("-100"))
        whenever(subTransactionPort.createVirtualParent(any(), eq(date), eq("Unterkunft"), eq(null), eq(orgId))).thenReturn(virtualParent)
        val excludedTx1 = tx1.copy(excluded = true, parentId = 99L)
        val excludedTx2 = tx2.copy(excluded = true, parentId = 99L)
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId))
            .thenReturn(tx1)
            .thenReturn(excludedTx1)
        whenever(transactionRepository.findByIdAndOrganizationId(2L, orgId))
            .thenReturn(tx2)
            .thenReturn(excludedTx2)

        val result =
            service.mergeTransactions(
                MergeTransactionsCommand(
                    transactionIds = listOf(1L, 2L),
                    accountingDate = date,
                    name = "Unterkunft",
                    comment = null,
                    organizationId = orgId,
                ),
            )

        verify(subTransactionPort).setParentId(1L, orgId, 99L)
        verify(subTransactionPort).setParentId(2L, orgId, 99L)
        verify(subTransactionPort).setExcluded(1L, orgId, true)
        verify(subTransactionPort).setExcluded(2L, orgId, true)
        assertThat(result.parent.id).isEqualTo(99L)
        assertThat(result.children).hasSize(2)
    }

    @Test
    fun `mergeTransactions should fail when fewer than 2 transactions`() {
        assertThatThrownBy {
            service.mergeTransactions(
                MergeTransactionsCommand(
                    transactionIds = listOf(1L),
                    accountingDate = date,
                    name = null,
                    comment = null,
                    organizationId = orgId,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mergeTransactions should fail when transactions from different accounts`() {
        val tx1 = physicalTx(1L).copy(accountIban = "DE00IBAN1")
        val tx2 = physicalTx(2L).copy(accountIban = "DE00IBAN2")
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(tx1)
        whenever(transactionRepository.findByIdAndOrganizationId(2L, orgId)).thenReturn(tx2)

        assertThatThrownBy {
            service.mergeTransactions(
                MergeTransactionsCommand(
                    transactionIds = listOf(1L, 2L),
                    accountingDate = date,
                    name = null,
                    comment = null,
                    organizationId = orgId,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("same account")
    }

    @Test
    fun `mergeTransactions should fail when a transaction has offset links`() {
        val offsetLink =
            TransactionOffsetLink(
                id = 1L,
                linkedTransactionId = 3L,
                linkedTransactionAmount = BigDecimal("30"),
                amountA = null,
                amountB = null,
                myCommitted = BigDecimal("-30"),
                groupId = null,
            )
        val tx1 = physicalTx(1L, BigDecimal("-30")).copy(offsetLinks = listOf(offsetLink))
        val tx2 = physicalTx(2L, BigDecimal("-70"))
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(tx1)
        whenever(transactionRepository.findByIdAndOrganizationId(2L, orgId)).thenReturn(tx2)

        assertThatThrownBy {
            service.mergeTransactions(
                MergeTransactionsCommand(
                    transactionIds = listOf(1L, 2L),
                    accountingDate = date,
                    name = null,
                    comment = null,
                    organizationId = orgId,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("offset links")
    }

    @Test
    fun `unmergeTransactions should restore physicals and delete virtual parent`() {
        val virtualParent = virtualTx(99L, BigDecimal("-100"))
        val child1 = physicalTx(1L, BigDecimal("-30"), excluded = true, parentId = 99L)
        val child2 = physicalTx(2L, BigDecimal("-70"), excluded = true, parentId = 99L)
        whenever(transactionRepository.findByIdAndOrganizationId(99L, orgId)).thenReturn(virtualParent)
        whenever(subTransactionPort.findChildren(99L, orgId)).thenReturn(listOf(child1, child2))

        service.unmergeTransactions(99L, orgId)

        verify(subTransactionPort).setParentId(1L, orgId, null)
        verify(subTransactionPort).setParentId(2L, orgId, null)
        verify(subTransactionPort).setExcluded(1L, orgId, false)
        verify(subTransactionPort).setExcluded(2L, orgId, false)
        verify(subTransactionPort).deleteVirtual(99L, orgId)
    }

    @Test
    fun `createVirtualTransaction should delegate to port and return result`() {
        val created = virtualTx(42L, BigDecimal("-55"))
        whenever(
            subTransactionPort.createStandaloneVirtual(
                amount = BigDecimal("-55"),
                currency = "EUR",
                accountIban = iban,
                accountingDate = date,
                categoryId = null,
                counterpartyName = "Shell",
                purpose = "Tankstelle",
                organizationId = orgId,
            ),
        ).thenReturn(created)

        val result =
            service.createVirtualTransaction(
                CreateVirtualTransactionCommand(
                    amount = BigDecimal("-55"),
                    currency = "EUR",
                    accountIban = iban,
                    accountingDate = date,
                    categoryId = null,
                    counterpartyName = "Shell",
                    purpose = "Tankstelle",
                    organizationId = orgId,
                ),
            )

        assertThat(result.id).isEqualTo(42L)
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("-55"))
    }

    @Test
    fun `createVirtualTransaction should fail when IBAN is blank`() {
        assertThatThrownBy {
            service.createVirtualTransaction(
                CreateVirtualTransactionCommand(
                    amount = BigDecimal("-10"),
                    currency = "EUR",
                    accountIban = "",
                    accountingDate = date,
                    categoryId = null,
                    counterpartyName = null,
                    purpose = null,
                    organizationId = orgId,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("IBAN")
    }

    @Test
    fun `removeFromMerge should fail when only 2 children remain`() {
        val child1 = physicalTx(1L, BigDecimal("-30"), excluded = true, parentId = 99L)
        val child2 = physicalTx(2L, BigDecimal("-70"), excluded = true, parentId = 99L)
        val virtualParent = virtualTx(99L, BigDecimal("-100"))
        whenever(transactionRepository.findByIdAndOrganizationId(1L, orgId)).thenReturn(child1)
        whenever(transactionRepository.findByIdAndOrganizationId(99L, orgId)).thenReturn(virtualParent)
        whenever(subTransactionPort.findChildren(99L, orgId)).thenReturn(listOf(child1, child2))

        assertThatThrownBy {
            service.removeFromMerge(1L, orgId)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("at least 2 transactions must remain")

        verify(subTransactionPort, never()).setParentId(any(), any(), any())
    }

    @Test
    fun `deleteVirtualTransaction should delete standalone virtual transaction`() {
        val tx = virtualTx(5L, BigDecimal("-50"))
        whenever(transactionRepository.findByIdAndOrganizationId(5L, orgId)).thenReturn(tx)
        whenever(subTransactionPort.findChildren(5L, orgId)).thenReturn(emptyList())

        service.deleteVirtualTransaction(5L, orgId)

        verify(subTransactionPort).deleteVirtual(5L, orgId)
    }

    @Test
    fun `deleteVirtualTransaction should fail when transaction is not virtual`() {
        val tx = physicalTx(5L)
        whenever(transactionRepository.findByIdAndOrganizationId(5L, orgId)).thenReturn(tx)

        assertThatThrownBy {
            service.deleteVirtualTransaction(5L, orgId)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Not a standalone virtual transaction")

        verify(subTransactionPort, never()).deleteVirtual(any(), any())
    }

    @Test
    fun `deleteVirtualTransaction should fail when virtual transaction has merge children`() {
        val parent = virtualTx(5L, BigDecimal("-100"))
        val child1 = physicalTx(1L, BigDecimal("-60"), excluded = true, parentId = 5L)
        val child2 = physicalTx(2L, BigDecimal("-40"), excluded = true, parentId = 5L)
        whenever(transactionRepository.findByIdAndOrganizationId(5L, orgId)).thenReturn(parent)
        whenever(subTransactionPort.findChildren(5L, orgId)).thenReturn(listOf(child1, child2))

        assertThatThrownBy {
            service.deleteVirtualTransaction(5L, orgId)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("merge children")

        verify(subTransactionPort, never()).deleteVirtual(any(), any())
    }
}
