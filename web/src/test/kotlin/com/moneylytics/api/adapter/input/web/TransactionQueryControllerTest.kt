package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionAccountingDateUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCommentUseCase
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.User
import java.math.BigDecimal
import java.time.LocalDate

class TransactionQueryControllerTest {
    private val getTransactionsUseCase: GetTransactionsUseCase = mock()
    private val resolveUserUseCase: ResolveUserUseCase = mock { on { resolveUser(any()) } doReturn USER_ID }
    private val principal =
        User
            .withUsername("testUser")
            .password("x")
            .roles("USER")
            .build()
    private val updateTransactionCategoryUseCase: UpdateTransactionCategoryUseCase = mock()
    private val updateTransactionCommentUseCase: UpdateTransactionCommentUseCase = mock()
    private val updateTransactionAccountingDateUseCase: UpdateTransactionAccountingDateUseCase = mock()
    private val controller =
        TransactionQueryController(
            getTransactionsUseCase,
            resolveUserUseCase,
            updateTransactionCategoryUseCase,
            updateTransactionCommentUseCase,
            updateTransactionAccountingDateUseCase,
        )

    private val from = LocalDate.of(2025, 1, 1)
    private val to = LocalDate.of(2025, 1, 31)

    @Test
    fun `should return nodes for each distinct category and subcategory`() =
        runTest {
            // Arrange
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, USER_ID, onlyNegative = true),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", subcategory = "Groceries", amount = BigDecimal("-50.00")),
                    transaction(category = "Food", subcategory = "Restaurant", amount = BigDecimal("-30.00")),
                    transaction(category = "Transport", subcategory = "Fuel", amount = BigDecimal("-40.00")),
                ),
            )

            // Act
            val response = controller.getSankeyData(from, to, principal = principal)

            // Assert
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
            // Arrange
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, USER_ID, onlyNegative = true),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", subcategory = "Groceries", amount = BigDecimal("-50.00")),
                    transaction(category = "Food", subcategory = "Restaurant", amount = BigDecimal("-30.00")),
                    transaction(category = "Transport", subcategory = "Fuel", amount = BigDecimal("-40.00")),
                ),
            )

            // Act
            val response = controller.getSankeyData(from, to, principal = principal)

            // Assert
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
            // Arrange
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, USER_ID, onlyNegative = true),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", subcategory = "Groceries", amount = BigDecimal("-50.00")),
                    transaction(category = "Food", subcategory = "Groceries", amount = BigDecimal("-20.00")),
                    transaction(category = "Food", subcategory = "Restaurant", amount = BigDecimal("-30.00")),
                ),
            )

            // Act
            val response = controller.getSankeyData(from, to, principal = principal)

            // Assert
            val groceriesLink = response.links.single { response.nodes[it.target].name == "Groceries" }
            assertThat(groceriesLink.value).isEqualByComparingTo(BigDecimal("70.00"))
            val restaurantLink = response.links.single { response.nodes[it.target].name == "Restaurant" }
            assertThat(restaurantLink.value).isEqualByComparingTo(BigDecimal("30.00"))
        }

    @Test
    fun `should link source category index to target subcategory index`() =
        runTest {
            // Arrange
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, USER_ID, onlyNegative = true),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", subcategory = "Groceries", amount = BigDecimal("-50.00")),
                    transaction(category = "Transport", subcategory = "Fuel", amount = BigDecimal("-40.00")),
                ),
            )

            // Act
            val response = controller.getSankeyData(from, to, principal = principal)

            // Assert
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
                    GetTransactionsQuery(from, to, USER_ID, onlyNegative = true),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", subcategory = "Other", amount = BigDecimal("-20.00")),
                    transaction(category = "Transport", subcategory = "Other", amount = BigDecimal("-30.00")),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal)

            assertThat(response.nodes.map { it.name }).containsExactly("Food", "Transport", "Other", "Other")
            val otherIndices = response.nodes.mapIndexedNotNull { i, n -> i.takeIf { n.name == "Other" } }
            assertThat(otherIndices).hasSize(2)
            response.links.forEach { link -> assertThat(link.source).isNotEqualTo(link.target) }
        }

    @Test
    fun `should give category and subcategory with the same name distinct nodes`() =
        runTest {
            // "Insurance" is both a top-level category and a subcategory of "Auto".
            // If both map to the same index, D3 sankey merges them and produces a
            // degenerate layout where every link appears the same width.
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, USER_ID, onlyNegative = true),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Auto", subcategory = "Insurance", amount = BigDecimal("-435.00")),
                    transaction(category = "Insurance", subcategory = "Liability", amount = BigDecimal("-100.00")),
                    transaction(category = "Insurance", subcategory = "Home", amount = BigDecimal("-200.00")),
                ),
            )

            // Act
            val response = controller.getSankeyData(from, to, principal = principal)

            // Assert — four distinct nodes, not three
            assertThat(response.nodes.map { it.name })
                .containsExactlyInAnyOrder("Auto", "Insurance", "Insurance", "Liability", "Home")
            // Both "Insurance" nodes must have different indices
            val insuranceIndices = response.nodes.mapIndexedNotNull { i, n -> i.takeIf { n.name == "Insurance" } }
            assertThat(insuranceIndices).hasSize(2)
            // No link references the same index for source and target
            response.links.forEach { link -> assertThat(link.source).isNotEqualTo(link.target) }
        }

    @Test
    fun `should exclude transactions with positive amount`() =
        runTest {
            // Arrange
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, USER_ID, onlyNegative = true),
                ),
            ).thenReturn(
                listOf(
                    transaction(category = "Food", subcategory = "Groceries", amount = BigDecimal("-50.00")),
                ),
            )

            // Act
            val response = controller.getSankeyData(from, to, principal = principal)

            // Assert
            assertThat(response.nodes.map { it.name }).containsExactly("Food", "Groceries")
        }

    @Test
    fun `should return empty nodes and links when no transactions exist`() =
        runTest {
            // Arrange
            whenever(
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, USER_ID, onlyNegative = true),
                ),
            ).thenReturn(emptyList())

            // Act
            val response = controller.getSankeyData(from, to, principal = principal)

            // Assert
            assertThat(response.nodes).isEmpty()
            assertThat(response.links).isEmpty()
        }

    private fun transaction(
        category: String,
        subcategory: String,
        amount: BigDecimal,
    ) = Transaction(
        category = category,
        subcategory = subcategory,
        bookingDate = from,
        valueDate = from,
        accountingDate = from,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00000000000000000000",
    )

    companion object {
        private const val USER_ID = 1L
    }
}
