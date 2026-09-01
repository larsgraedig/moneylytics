package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AcceptSuggestionUseCase
import com.moneylytics.api.application.port.input.AcceptSuggestionsBatchUseCase
import com.moneylytics.api.application.port.input.BulkUpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.GetBurnRateUseCase
import com.moneylytics.api.application.port.input.GetCashflowUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.GetCategoryTotalsUseCase
import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.TransactionType
import com.moneylytics.api.application.port.input.UpdateExcludeFromSuggestionsUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionAccountingDateUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCommentUseCase
import com.moneylytics.api.domain.Category
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
    private val getCategoriesUseCase: GetCategoriesUseCase = mock()
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
    private val updateExcludeFromSuggestionsUseCase: UpdateExcludeFromSuggestionsUseCase = mock()
    private val acceptSuggestionUseCase: AcceptSuggestionUseCase = mock()
    private val acceptSuggestionsBatchUseCase: AcceptSuggestionsBatchUseCase = mock()
    private val bulkUpdateTransactionCategoryUseCase: BulkUpdateTransactionCategoryUseCase = mock()
    private val controller =
        TransactionQueryController(
            getTransactionsUseCase,
            getCategoriesUseCase,
            getCashflowUseCase,
            getBurnRateUseCase,
            getCategoryTotalsUseCase,
            resolveOrganizationUseCase,
            updateTransactionCategoryUseCase,
            updateTransactionCommentUseCase,
            updateTransactionAccountingDateUseCase,
            updateExcludeFromSuggestionsUseCase,
            acceptSuggestionUseCase,
            acceptSuggestionsBatchUseCase,
            bulkUpdateTransactionCategoryUseCase,
        )

    private val from = LocalDate.of(2025, 1, 1)
    private val to = LocalDate.of(2025, 1, 31)

    // Category IDs for 2-level hierarchy: Food > Groceries/Restaurant, Transport > Fuel
    private val foodId = 1L
    private val groceriesId = 2L
    private val restaurantId = 3L
    private val transportId = 4L
    private val fuelId = 5L

    private val twoLevelCategories =
        listOf(
            cat(foodId, "Food"),
            cat(groceriesId, "Groceries", parentId = foodId),
            cat(restaurantId, "Restaurant", parentId = foodId),
            cat(transportId, "Transport"),
            cat(fuelId, "Fuel", parentId = transportId),
        )

    @Test
    fun `should return nodes for each distinct category and subcategory`() =
        runTest {
            givenTransactions(
                tx(categoryId = groceriesId, amount = BigDecimal("-50.00")),
                tx(categoryId = restaurantId, amount = BigDecimal("-30.00")),
                tx(categoryId = fuelId, amount = BigDecimal("-40.00")),
            )
            givenCategories(twoLevelCategories)

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes.map { it.name }).containsExactlyInAnyOrder(
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
            givenTransactions(
                tx(categoryId = groceriesId, amount = BigDecimal("-50.00")),
                tx(categoryId = restaurantId, amount = BigDecimal("-30.00")),
                tx(categoryId = fuelId, amount = BigDecimal("-40.00")),
            )
            givenCategories(twoLevelCategories)

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            val nodeByName = response.nodes.associateBy { it.name }
            assertThat(nodeByName.getValue("Food").value).isEqualByComparingTo(BigDecimal("80.00"))
            assertThat(nodeByName.getValue("Transport").value).isEqualByComparingTo(BigDecimal("40.00"))
            assertThat(nodeByName.getValue("Groceries").value).isEqualByComparingTo(BigDecimal("50.00"))
            assertThat(nodeByName.getValue("Restaurant").value).isEqualByComparingTo(BigDecimal("30.00"))
            assertThat(nodeByName.getValue("Fuel").value).isEqualByComparingTo(BigDecimal("40.00"))
        }

    @Test
    fun `should aggregate amounts for the same category-pair`() =
        runTest {
            givenTransactions(
                tx(categoryId = groceriesId, amount = BigDecimal("-50.00")),
                tx(categoryId = groceriesId, amount = BigDecimal("-20.00")),
                tx(categoryId = restaurantId, amount = BigDecimal("-30.00")),
            )
            givenCategories(
                listOf(
                    cat(foodId, "Food"),
                    cat(groceriesId, "Groceries", parentId = foodId),
                    cat(restaurantId, "Restaurant", parentId = foodId),
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
            givenTransactions(
                tx(categoryId = groceriesId, amount = BigDecimal("-50.00")),
                tx(categoryId = fuelId, amount = BigDecimal("-40.00")),
            )
            givenCategories(twoLevelCategories)

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            val nodeNames = response.nodes.map { it.name }
            val foodIndex = response.nodes.indexOfFirst { it.categoryId == foodId }
            val transportIndex = response.nodes.indexOfFirst { it.categoryId == transportId }
            val groceriesIndex = response.nodes.indexOfFirst { it.categoryId == groceriesId }
            val fuelIndex = response.nodes.indexOfFirst { it.categoryId == fuelId }

            val foodLink = response.links.single { it.source == foodIndex }
            assertThat(foodLink.target).isEqualTo(groceriesIndex)

            val transportLink = response.links.single { it.source == transportIndex }
            assertThat(transportLink.target).isEqualTo(fuelIndex)

            assertThat(nodeNames).isNotEmpty()
        }

    @Test
    fun `should give the same subcategory name under different categories distinct nodes`() =
        runTest {
            val otherId1 = 10L
            val otherId2 = 11L
            givenTransactions(
                tx(categoryId = otherId1, amount = BigDecimal("-20.00")),
                tx(categoryId = otherId2, amount = BigDecimal("-30.00")),
            )
            givenCategories(
                listOf(
                    cat(foodId, "Food"),
                    cat(transportId, "Transport"),
                    cat(otherId1, "Other", parentId = foodId),
                    cat(otherId2, "Other", parentId = transportId),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes.map { it.name }).containsExactlyInAnyOrder("Food", "Transport", "Other", "Other")
            val otherIndices = response.nodes.mapIndexedNotNull { i, n -> i.takeIf { n.name == "Other" } }
            assertThat(otherIndices).hasSize(2)
            response.links.forEach { link -> assertThat(link.source).isNotEqualTo(link.target) }
        }

    @Test
    fun `should give category and subcategory with the same name distinct nodes`() =
        runTest {
            val autoId = 1L
            val insuranceAsChildId = 2L
            val insuranceAsRootId = 3L
            val liabilityId = 4L
            val homeId = 5L
            givenTransactions(
                tx(categoryId = insuranceAsChildId, amount = BigDecimal("-435.00")),
                tx(categoryId = liabilityId, amount = BigDecimal("-100.00")),
                tx(categoryId = homeId, amount = BigDecimal("-200.00")),
            )
            givenCategories(
                listOf(
                    cat(autoId, "Auto"),
                    cat(insuranceAsChildId, "Insurance", parentId = autoId),
                    cat(insuranceAsRootId, "Insurance"),
                    cat(liabilityId, "Liability", parentId = insuranceAsRootId),
                    cat(homeId, "Home", parentId = insuranceAsRootId),
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
    fun `should skip transactions without categoryId`() =
        runTest {
            givenTransactions(
                tx(categoryId = groceriesId, amount = BigDecimal("-50.00")),
                Transaction(
                    categoryId = null,
                    bookingDate = from,
                    valueDate = from,
                    accountingDate = from,
                    amount = BigDecimal("-30.00"),
                    currency = "EUR",
                    accountIban = "DE00000000000000000000",
                ),
            )
            givenCategories(
                listOf(
                    cat(foodId, "Food"),
                    cat(groceriesId, "Groceries", parentId = foodId),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes.map { it.name }).containsExactlyInAnyOrder("Food", "Groceries")
        }

    @Test
    fun `should support three-level hierarchy`() =
        runTest {
            val supermarktId = 2L
            val bioId = 4L
            val konvId = 5L
            givenTransactions(
                tx(categoryId = bioId, amount = BigDecimal("-60.00")),
                tx(categoryId = konvId, amount = BigDecimal("-40.00")),
                tx(categoryId = restaurantId, amount = BigDecimal("-50.00")),
            )
            givenCategories(
                listOf(
                    cat(foodId, "Food"),
                    cat(supermarktId, "Supermarkt", parentId = foodId),
                    cat(restaurantId, "Restaurant", parentId = foodId),
                    cat(bioId, "Bio", parentId = supermarktId),
                    cat(konvId, "Konventionell", parentId = supermarktId),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes.map { it.name })
                .containsExactlyInAnyOrder("Food", "Supermarkt", "Restaurant", "Bio", "Konventionell")
            val nodeNames = response.nodes.map { it.name }
            val foodIdx = response.nodes.indexOfFirst { it.categoryId == foodId }
            val supermarktIdx = response.nodes.indexOfFirst { it.categoryId == supermarktId }
            val restaurantIdx = response.nodes.indexOfFirst { it.categoryId == restaurantId }
            val bioIdx = response.nodes.indexOfFirst { it.categoryId == bioId }
            val konvIdx = response.nodes.indexOfFirst { it.categoryId == konvId }
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
            assertThat(nodeNames).isNotEmpty()
        }

    @Test
    fun `should include namePath for each node`() =
        runTest {
            val supermarktId = 2L
            val bioId = 3L
            givenTransactions(tx(categoryId = bioId, amount = BigDecimal("-50.00")))
            givenCategories(
                listOf(
                    cat(foodId, "Food"),
                    cat(supermarktId, "Supermarkt", parentId = foodId),
                    cat(bioId, "Bio", parentId = supermarktId),
                ),
            )

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            val bioNode = response.nodes.single { it.name == "Bio" }
            assertThat(bioNode.namePath).containsExactly("Food", "Supermarkt", "Bio")
            val foodNode = response.nodes.single { it.name == "Food" }
            assertThat(foodNode.namePath).containsExactly("Food")
        }

    @Test
    fun `should return empty nodes and links when no transactions exist`() =
        runTest {
            givenTransactions()
            givenCategories(emptyList())

            val response = controller.getSankeyData(from, to, principal = principal, exchange = exchange)

            assertThat(response.nodes).isEmpty()
            assertThat(response.links).isEmpty()
        }

    private fun givenTransactions(vararg transactions: Transaction) {
        whenever(
            getTransactionsUseCase.getTransactions(
                GetTransactionsQuery(from, to, ORG_ID, type = TransactionType.EXPENSES),
            ),
        ).thenReturn(transactions.toList())
    }

    private fun givenCategories(categories: List<Category>) {
        whenever(getCategoriesUseCase.getCategories(ORG_ID)).thenReturn(categories)
    }

    @Test
    fun `should return updated transaction when exclude from suggestions flag is set`() =
        runTest {
            val txId = 10L
            val updatedTx =
                Transaction(
                    id = txId,
                    bookingDate = from,
                    valueDate = from,
                    accountingDate = from,
                    amount = BigDecimal("-50.00"),
                    currency = "EUR",
                    accountIban = "DE00000000000000000000",
                    excludeFromSuggestions = true,
                )
            whenever(updateExcludeFromSuggestionsUseCase.updateExcludeFromSuggestions(txId, ORG_ID, true))
                .thenReturn(updatedTx)

            val response = controller.updateExcludeFromSuggestions(txId, UpdateExcludeFromSuggestionsRequest(true), principal, exchange)

            assertThat(response.statusCode.value()).isEqualTo(200)
            assertThat(response.body?.excludeFromSuggestions).isTrue()
        }

    @Test
    fun `should return 404 when transaction not found for exclude from suggestions`() =
        runTest {
            val txId = 99L
            whenever(updateExcludeFromSuggestionsUseCase.updateExcludeFromSuggestions(txId, ORG_ID, true)).thenReturn(null)

            val response = controller.updateExcludeFromSuggestions(txId, UpdateExcludeFromSuggestionsRequest(true), principal, exchange)

            assertThat(response.statusCode.value()).isEqualTo(404)
        }

    private fun tx(
        categoryId: Long,
        amount: BigDecimal,
    ) = Transaction(
        categoryId = categoryId,
        bookingDate = from,
        valueDate = from,
        accountingDate = from,
        amount = amount,
        currency = "EUR",
        accountIban = "DE00000000000000000000",
    )

    private fun cat(
        id: Long,
        name: String,
        parentId: Long? = null,
    ) = Category(id = id, name = name, parentId = parentId)

    companion object {
        private const val ORG_ID = 1L
    }
}
