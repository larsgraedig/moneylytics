package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.BulkUpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.GetBurnRateUseCase
import com.moneylytics.api.application.port.input.GetCashflowUseCase
import com.moneylytics.api.application.port.input.GetCategoryTotalsUseCase
import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.TransactionType
import com.moneylytics.api.application.port.input.UpdateTransactionAccountingDateUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCommentUseCase
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.User
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.time.LocalDate

class TransactionQueryControllerTest {
    private val getTransactionsUseCase: GetTransactionsUseCase = mock()
    private val getCashflowUseCase: GetCashflowUseCase = mock()
    private val getBurnRateUseCase: GetBurnRateUseCase = mock()
    private val getCategoryTotalsUseCase: GetCategoryTotalsUseCase = mock()
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase = ResolveOrganizationUseCase { _, _ -> ORG_ID }
    private val exchange: ServerWebExchange = mock()
    private val principal =
        User
            .withUsername("testUser")
            .password("x")
            .roles("USER")
            .build()
    private val updateTransactionCategoryUseCase: UpdateTransactionCategoryUseCase = mock()
    private val updateTransactionCommentUseCase: UpdateTransactionCommentUseCase = mock()
    private val updateTransactionAccountingDateUseCase: UpdateTransactionAccountingDateUseCase = mock()
    private val bulkUpdateTransactionCategoryUseCase: BulkUpdateTransactionCategoryUseCase = mock()
    private val controller =
        TransactionQueryController(
            getTransactionsUseCase,
            getCashflowUseCase,
            getBurnRateUseCase,
            getCategoryTotalsUseCase,
            resolveOrganizationUseCase,
            updateTransactionCategoryUseCase,
            updateTransactionCommentUseCase,
            updateTransactionAccountingDateUseCase,
            bulkUpdateTransactionCategoryUseCase,
        )

    private val from = LocalDate.of(2025, 1, 1)
    private val to = LocalDate.of(2025, 1, 31)

    @Test
    fun `should return nodes for each distinct category and subcategory`() =
        runTest {
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", group = "Groceries", amount = BigDecimal("-50.00")),
                    transaction(category = "Food", group = "Restaurant", amount = BigDecimal("-30.00")),
                    transaction(category = "Transport", group = "Fuel", amount = BigDecimal("-40.00")),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes.map { it.name }).containsExactly(
                "Food",
                "Transport",
                "Groceries",
                "Restaurant",
                "Fuel",
            )
        }

    @Test
    fun `should set node value to the total flow through that node`() =
        runTest {
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", group = "Groceries", amount = BigDecimal("-50.00")),
                    transaction(category = "Food", group = "Restaurant", amount = BigDecimal("-30.00")),
                    transaction(category = "Transport", group = "Fuel", amount = BigDecimal("-40.00")),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            val nodeByName = response.nodes.associateBy { it.name }
            assertThat(nodeByName.getValue("Food").value).isEqualByComparingTo(BigDecimal("80.00"))
            assertThat(nodeByName.getValue("Transport").value).isEqualByComparingTo(BigDecimal("40.00"))
            assertThat(nodeByName.getValue("Groceries").value).isEqualByComparingTo(BigDecimal("50.00"))
            assertThat(nodeByName.getValue("Restaurant").value).isEqualByComparingTo(BigDecimal("30.00"))
            assertThat(nodeByName.getValue("Fuel").value).isEqualByComparingTo(BigDecimal("40.00"))
        }

    @Test
    fun `should aggregate amounts per category-subcategory pair and use absolute values`() =
        runTest {
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", group = "Groceries", amount = BigDecimal("-50.00")),
                    transaction(category = "Food", group = "Groceries", amount = BigDecimal("-20.00")),
                    transaction(category = "Food", group = "Restaurant", amount = BigDecimal("-30.00")),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            val groceriesLink = response.links.single { response.nodes[it.target].name == "Groceries" }
            assertThat(groceriesLink.value).isEqualByComparingTo(BigDecimal("70.00"))
            val restaurantLink = response.links.single { response.nodes[it.target].name == "Restaurant" }
            assertThat(restaurantLink.value).isEqualByComparingTo(BigDecimal("30.00"))
        }

    @Test
    fun `should link source category index to target subcategory index`() =
        runTest {
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", group = "Groceries", amount = BigDecimal("-50.00")),
                    transaction(category = "Transport", group = "Fuel", amount = BigDecimal("-40.00")),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            val nodeNames = response.nodes.map { it.name }
            val foodIndex = nodeNames.indexOf("Food")
            val transportIndex = nodeNames.indexOf("Transport")
            val groceriesIndex = nodeNames.indexOf("Groceries")
            val fuelIndex = nodeNames.indexOf("Fuel")

            val foodLink = response.links.single { it.source == foodIndex }
            assertThat(foodLink.target).isEqualTo(groceriesIndex)

            val transportLink = response.links.single { it.source == transportIndex }
            assertThat(transportLink.target).isEqualTo(fuelIndex)
        }

    @Test
    fun `should give the same subcategory name under different categories distinct right-side nodes`() =
        runTest {
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", group = "Other", amount = BigDecimal("-20.00")),
                    transaction(category = "Transport", group = "Other", amount = BigDecimal("-30.00")),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes.map { it.name }).containsExactly("Food", "Transport", "Other", "Other")
            val otherIndices = response.nodes.mapIndexedNotNull { i, n -> i.takeIf { n.name == "Other" } }
            assertThat(otherIndices).hasSize(2)
            response.links.forEach { link -> assertThat(link.source).isNotEqualTo(link.target) }
        }

    @Test
    fun `should give category and subcategory with the same name distinct nodes`() =
        runTest {
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Auto", group = "Insurance", amount = BigDecimal("-435.00")),
                    transaction(category = "Insurance", group = "Liability", amount = BigDecimal("-100.00")),
                    transaction(category = "Insurance", group = "Home", amount = BigDecimal("-200.00")),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes.map { it.name })
                .containsExactlyInAnyOrder("Auto", "Insurance", "Insurance", "Liability", "Home")
            val insuranceIndices = response.nodes.mapIndexedNotNull { i, n -> i.takeIf { n.name == "Insurance" } }
            assertThat(insuranceIndices).hasSize(2)
            response.links.forEach { link -> assertThat(link.source).isNotEqualTo(link.target) }
        }

    @Test
    fun `should exclude transactions with positive amount`() =
        runTest {
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", group = "Groceries", amount = BigDecimal("-50.00")),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes.map { it.name }).containsExactly("Food", "Groceries")
        }

    @Test
    fun `should add leaf node and group-to-leaf link when subcategory is set`() =
        runTest {
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", group = "Supermarkt", amount = BigDecimal("-60.00"), subcategory = "Bio"),
                    transaction(category = "Food", group = "Supermarkt", amount = BigDecimal("-40.00"), subcategory = "Konventionell"),
                    transaction(category = "Food", group = "Restaurant", amount = BigDecimal("-50.00")),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes.map { it.name })
                .containsExactlyInAnyOrder("Food", "Supermarkt", "Restaurant", "Bio", "Konventionell")
            val nodeNames = response.nodes.map { it.name }
            val foodIdx = nodeNames.indexOf("Food")
            val supermarktIdx = nodeNames.indexOf("Supermarkt")
            val restaurantIdx = nodeNames.indexOf("Restaurant")
            val bioIdx = nodeNames.indexOf("Bio")
            val konvIdx = nodeNames.indexOf("Konventionell")
            assertThat(response.links).anySatisfy { link ->
                assertThat(link.source).isEqualTo(foodIdx)
                assertThat(link.target).isEqualTo(supermarktIdx)
                assertThat(link.value).isEqualByComparingTo(BigDecimal("100.00"))
            }
            assertThat(response.links).anySatisfy { link ->
                assertThat(link.source).isEqualTo(foodIdx)
                assertThat(link.target).isEqualTo(restaurantIdx)
                assertThat(link.value).isEqualByComparingTo(BigDecimal("50.00"))
            }
            assertThat(response.links).anySatisfy { link ->
                assertThat(link.source).isEqualTo(supermarktIdx)
                assertThat(link.target).isEqualTo(bioIdx)
                assertThat(link.value).isEqualByComparingTo(BigDecimal("60.00"))
            }
            assertThat(response.links).anySatisfy { link ->
                assertThat(link.source).isEqualTo(supermarktIdx)
                assertThat(link.target).isEqualTo(konvIdx)
                assertThat(link.value).isEqualByComparingTo(BigDecimal("40.00"))
            }
        }

    @Test
    fun `should return empty nodes and links when no transactions exist`() =
        runTest {
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
                ),
            ).thenReturn(emptyList())

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes).isEmpty()
            assertThat(response.links).isEmpty()
        }

    private fun transaction(
        category: String,
        group: String,
        amount: BigDecimal,
        subcategory: String? = null,
    ) = Transaction(
        category = category,
        group = group,
        subcategory = subcategory,
        bookingDate = from,
        valueDate = from,
        accountingDate = from,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00000000000000000000",
    )

    companion object {
        private const val ORG_ID = 1L
    }
}
