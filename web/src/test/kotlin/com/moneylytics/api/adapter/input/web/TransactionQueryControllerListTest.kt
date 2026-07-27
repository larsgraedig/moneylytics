package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.BulkCategoryUpdate
import com.moneylytics.api.application.port.input.BulkUpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.GetBurnRateUseCase
import com.moneylytics.api.application.port.input.GetCashflowUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.GetCategoryTotalsUseCase
import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionAccountingDateUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCommentUseCase
import com.moneylytics.api.domain.Transaction
import com.moneylytics.api.domain.TransactionOffsetLink
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.time.LocalDate

class TransactionQueryControllerListTest {
    private val organizationId = 1L
    private val exchange: ServerWebExchange = mock()
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase = ResolveOrganizationUseCase { _, _ -> organizationId }
    private val getTransactionsUseCase: GetTransactionsUseCase = mock()
    private val getCashflowUseCase: GetCashflowUseCase = mock()
    private val getBurnRateUseCase: GetBurnRateUseCase = mock()
    private val getCategoriesUseCase: GetCategoriesUseCase = mock { on { getCategories(any()) } doReturn emptyList() }
    private val getCategoryTotalsUseCase: GetCategoryTotalsUseCase = mock()
    private val updateTransactionCategoryUseCase: UpdateTransactionCategoryUseCase = mock()
    private val updateTransactionCommentUseCase: UpdateTransactionCommentUseCase = mock()
    private val updateTransactionAccountingDateUseCase: UpdateTransactionAccountingDateUseCase = mock()
    private val bulkUpdateTransactionCategoryUseCase: BulkUpdateTransactionCategoryUseCase = mock()
    private val controller =
        TransactionQueryController(
            getTransactionsUseCase,
            getCashflowUseCase,
            getBurnRateUseCase,
            getCategoriesUseCase,
            getCategoryTotalsUseCase,
            resolveOrganizationUseCase,
            updateTransactionCategoryUseCase,
            updateTransactionCommentUseCase,
            updateTransactionAccountingDateUseCase,
            bulkUpdateTransactionCategoryUseCase,
        )
    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()
    private val from = LocalDate.of(2025, 1, 1)
    private val to = LocalDate.of(2025, 1, 31)

    @Test
    fun `should sort transactions by accountingDate descending`() =
        runTest {
            val jan5 = tx(id = 1L, accountingDate = LocalDate.of(2025, 1, 5))
            val jan20 = tx(id = 2L, accountingDate = LocalDate.of(2025, 1, 20))
            val jan10 = tx(id = 3L, accountingDate = LocalDate.of(2025, 1, 10))
            whenever(getTransactionsUseCase.getTransactions(any())).thenReturn(listOf(jan5, jan20, jan10))

            val response = controller.listTransactions(from, to, principal = principal, exchange = exchange)

            assertThat(response.transactions.map { it.id }).containsExactly(2L, 3L, 1L)
        }

    @Test
    fun `should compute total using effectiveAmount`() =
        runTest {
            val txWithOffset =
                tx(
                    id = 1L,
                    amount = BigDecimal("-100"),
                    offsetLink = offsetLink(amountA = BigDecimal("-60"), amountB = BigDecimal("60")),
                )
            val txPlain = tx(id = 2L, amount = BigDecimal("-50"))
            whenever(getTransactionsUseCase.getTransactions(any())).thenReturn(listOf(txWithOffset, txPlain))

            val response = controller.listTransactions(from, to, principal = principal, exchange = exchange)

            assertThat(response.total).isEqualByComparingTo(BigDecimal("-90"))
        }

    @Test
    fun `should pass all filter params to use case`() =
        runTest {
            whenever(getTransactionsUseCase.getTransactions(any())).thenReturn(emptyList())

            controller.listTransactions(
                from,
                to,
                category = "Lebensmittel",
                subcategory = "Supermarkt",
                categoryGroup = "Konsum",
                iban = "DE00TEST",
                uncategorized = true,
                excludeCollectionId = 5L,
                excludeBudgetId = 7L,
                principal = principal,
                exchange = exchange,
            )

            val captor = argumentCaptor<GetTransactionsQuery>()
            verify(getTransactionsUseCase).getTransactions(captor.capture())
            val query = captor.firstValue
            assertThat(query.category).isEqualTo("Lebensmittel")
            assertThat(query.subcategory).isEqualTo("Supermarkt")
            assertThat(query.categoryGroup).isEqualTo("Konsum")
            assertThat(query.accountIban).isEqualTo("DE00TEST")
            assertThat(query.uncategorized).isTrue()
            assertThat(query.excludeCollectionId).isEqualTo(5L)
            assertThat(query.excludeBudgetId).isEqualTo(7L)
        }

    @Test
    fun `should return 404 when updateTransactionCategory returns null`() =
        runTest {
            whenever(updateTransactionCategoryUseCase.updateCategory(any(), any(), any(), any(), anyOrNull())).thenReturn(null)

            val response = controller.updateTransactionCategory(99L, UpdateCategoryRequest("Kat", "Sub"), principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should return 200 with item when updateTransactionCategory succeeds`() =
        runTest {
            val updated = tx(id = 1L)
            whenever(updateTransactionCategoryUseCase.updateCategory(any(), any(), any(), any(), anyOrNull())).thenReturn(updated)

            val response =
                controller.updateTransactionCategory(
                    1L,
                    UpdateCategoryRequest("Lebensmittel", "Supermarkt"),
                    principal,
                    exchange,
                )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }

    @Test
    fun `should return 404 when updateTransactionComment returns null`() =
        runTest {
            whenever(updateTransactionCommentUseCase.updateComment(any(), any(), any())).thenReturn(null)

            val response = controller.updateTransactionComment(99L, UpdateCommentRequest("test"), principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should return 404 when updateTransactionAccountingDate returns null`() =
        runTest {
            whenever(updateTransactionAccountingDateUseCase.updateAccountingDate(any(), any(), any())).thenReturn(null)

            val response = controller.updateTransactionAccountingDate(99L, UpdateAccountingDateRequest(from), principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should map bulk update DTOs to domain and return items`() =
        runTest {
            val updated = listOf(tx(id = 1L), tx(id = 2L))
            whenever(bulkUpdateTransactionCategoryUseCase.bulkUpdateCategory(any(), any())).thenReturn(updated)

            val result =
                controller.bulkUpdateTransactionCategory(
                    BulkUpdateCategoryRequestDto(
                        updates =
                            listOf(
                                BulkCategoryUpdateDto(id = 1L, category = "Lebensmittel", subcategory = "Supermarkt"),
                                BulkCategoryUpdateDto(id = 2L, category = "Transport", subcategory = "ÖPNV"),
                            ),
                    ),
                    principal,
                    exchange,
                )

            assertThat(result).hasSize(2)
            val captor = argumentCaptor<List<BulkCategoryUpdate>>()
            verify(bulkUpdateTransactionCategoryUseCase).bulkUpdateCategory(captor.capture(), any())
            assertThat(captor.firstValue[0].id).isEqualTo(1L)
            assertThat(captor.firstValue[1].category).isEqualTo("Transport")
        }

    @Test
    fun `should parse 3-part series spec category colon group colon sub`() =
        runTest {
            whenever(getTransactionsUseCase.getTransactions(any())).thenReturn(emptyList())

            controller.getTrends(from, to, series = listOf("Lebensmittel:Konsum:Supermarkt"), principal = principal, exchange = exchange)

            val captor = argumentCaptor<GetTransactionsQuery>()
            verify(getTransactionsUseCase).getTransactions(captor.capture())
            assertThat(captor.firstValue.category).isEqualTo("Lebensmittel")
            assertThat(captor.firstValue.categoryGroup).isEqualTo("Konsum")
        }

    @Test
    fun `should parse 2-part series spec as category colon subcategory`() =
        runTest {
            whenever(getTransactionsUseCase.getTransactions(any())).thenReturn(emptyList())

            controller.getTrends(from, to, series = listOf("Transport:ÖPNV"), principal = principal, exchange = exchange)

            val captor = argumentCaptor<GetTransactionsQuery>()
            verify(getTransactionsUseCase).getTransactions(captor.capture())
            assertThat(captor.firstValue.category).isEqualTo("Transport")
            assertThat(captor.firstValue.categoryGroup).isNull()
        }

    @Test
    fun `should parse 1-part series spec as category only`() =
        runTest {
            whenever(getTransactionsUseCase.getTransactions(any())).thenReturn(emptyList())

            controller.getTrends(from, to, series = listOf("Freizeit"), principal = principal, exchange = exchange)

            val captor = argumentCaptor<GetTransactionsQuery>()
            verify(getTransactionsUseCase).getTransactions(captor.capture())
            assertThat(captor.firstValue.category).isEqualTo("Freizeit")
            assertThat(captor.firstValue.categoryGroup).isNull()
        }

    private fun tx(
        id: Long,
        amount: BigDecimal = BigDecimal("-50"),
        accountingDate: LocalDate = from,
        offsetLink: TransactionOffsetLink? = null,
    ) = Transaction(
        id = id,
        category = "Test",
        subcategory = "Test",
        bookingDate = from,
        valueDate = from,
        accountingDate = accountingDate,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00TEST",
        offsetLinks = if (offsetLink != null) listOf(offsetLink) else emptyList(),
    )

    private fun offsetLink(
        amountA: BigDecimal,
        amountB: BigDecimal,
    ) = TransactionOffsetLink(
        id = 99L,
        linkedTransactionId = 100L,
        linkedTransactionAmount = amountB,
        amountA = amountA,
        amountB = amountB,
        myCommitted = amountA,
        groupId = null,
    )
}
