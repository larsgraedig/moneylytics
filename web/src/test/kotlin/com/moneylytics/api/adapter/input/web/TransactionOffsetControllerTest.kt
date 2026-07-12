package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AllocationExceededException
import com.moneylytics.api.application.port.input.ExistingLinkSummary
import com.moneylytics.api.application.port.input.GetLinkedTransactionsUseCase
import com.moneylytics.api.application.port.input.LinkTransactionResult
import com.moneylytics.api.application.port.input.LinkedTransactionGroup
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import java.math.BigDecimal
import java.time.LocalDate

class TransactionOffsetControllerTest {
    private val userId = 1L
    private val resolveUserUseCase: ResolveUserUseCase = mock { on { resolveUser(any()) } doReturn userId }
    private val manageTransactionOffsetUseCase: ManageTransactionOffsetUseCase = mock()
    private val getLinkedTransactionsUseCase: GetLinkedTransactionsUseCase = mock()
    private val controller = TransactionOffsetController(manageTransactionOffsetUseCase, getLinkedTransactionsUseCase, resolveUserUseCase)
    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()
    private val date = LocalDate.of(2025, 1, 15)
    private val linkRequest = LinkTransactionRequest(otherTransactionId = 2L)

    @Test
    fun `should return 404 when linkTransactions throws IllegalArgumentException`() =
        runTest {
            whenever(manageTransactionOffsetUseCase.linkTransactions(any())).thenThrow(IllegalArgumentException("not found"))

            val response = controller.linkTransaction(1L, linkRequest, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should return 400 when linkTransactions throws IllegalStateException`() =
        runTest {
            whenever(manageTransactionOffsetUseCase.linkTransactions(any())).thenThrow(IllegalStateException("already linked"))

            val response = controller.linkTransaction(1L, linkRequest, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

    @Test
    fun `should return 422 with body when linkTransactions throws AllocationExceededException`() =
        runTest {
            val exception =
                AllocationExceededException(
                    transactionId = 1L,
                    maxRemaining = BigDecimal("20.00"),
                    existingLinks =
                        listOf(
                            ExistingLinkSummary(linkId = 5L, linkedTransactionId = 9L, committedAmount = BigDecimal("80.00")),
                        ),
                )
            whenever(manageTransactionOffsetUseCase.linkTransactions(any())).thenThrow(exception)

            val response = controller.linkTransaction(1L, linkRequest, principal)

            assertThat(response.statusCode.value()).isEqualTo(422)
            val body = response.body as AllocationErrorResponse
            assertThat(body.transactionId).isEqualTo(1L)
            assertThat(body.maxRemainingAmount).isEqualByComparingTo(BigDecimal("20.00"))
            assertThat(body.existingLinks).hasSize(1)
            assertThat(body.existingLinks[0].linkId).isEqualTo(5L)
        }

    @Test
    fun `should return 200 with LinkTransactionsResponse on successful link`() =
        runTest {
            val result =
                LinkTransactionResult(
                    groupId = 10L,
                    sourceTransaction = tx(1L),
                    otherTransaction = tx(2L),
                )
            whenever(manageTransactionOffsetUseCase.linkTransactions(any())).thenReturn(result)

            val response = controller.linkTransaction(1L, linkRequest, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            val body = response.body as LinkTransactionsResponse
            assertThat(body.groupId).isEqualTo(10L)
            assertThat(body.sourceTransaction.id).isEqualTo(1L)
            assertThat(body.otherTransaction.id).isEqualTo(2L)
        }

    @Test
    fun `should return 204 when unlinkTransactions succeeds`() =
        runTest {
            whenever(manageTransactionOffsetUseCase.unlinkTransactions(5L, userId)).thenReturn(true)

            val response = controller.unlinkTransaction(5L, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        }

    @Test
    fun `should return 404 when unlinkTransactions finds no link`() =
        runTest {
            whenever(manageTransactionOffsetUseCase.unlinkTransactions(99L, userId)).thenReturn(false)

            val response = controller.unlinkTransaction(99L, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should return 404 when getLinkedGroup returns null`() =
        runTest {
            whenever(getLinkedTransactionsUseCase.getLinkedGroup(99L, userId)).thenReturn(null)

            val response = controller.getLinkedGroup(99L, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should return 200 with group data when getLinkedGroup finds group`() =
        runTest {
            val group =
                LinkedTransactionGroup(
                    groupId = 10L,
                    name = "Rückerstattung",
                    comment = null,
                    transactions = listOf(tx(1L), tx(2L)),
                )
            whenever(getLinkedTransactionsUseCase.getLinkedGroup(10L, userId)).thenReturn(group)

            val response = controller.getLinkedGroup(10L, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            val body = response.body as LinkedGroupItem
            assertThat(body.groupId).isEqualTo(10L)
            assertThat(body.name).isEqualTo("Rückerstattung")
            assertThat(body.transactions).hasSize(2)
        }

    @Test
    fun `should map linked groups to LinkedGroupResponse`() =
        runTest {
            val groups =
                listOf(
                    LinkedTransactionGroup(groupId = 1L, name = "A", comment = null, transactions = listOf(tx(10L))),
                    LinkedTransactionGroup(groupId = 2L, name = "B", comment = null, transactions = listOf(tx(11L))),
                )
            whenever(getLinkedTransactionsUseCase.getLinkedGroups(userId)).thenReturn(groups)

            val response = controller.getLinkedTransactions(principal)

            assertThat(response.groups).hasSize(2)
            assertThat(response.groups.map { it.groupId }).containsExactly(1L, 2L)
        }

    private fun tx(id: Long) =
        Transaction(
            id = id,
            category = null,
            subcategory = null,
            bookingDate = date,
            valueDate = date,
            accountingDate = date,
            amount = BigDecimal("-100"),
            currency = "EUR",
            accountIban = "DE00TEST",
        )
}
