package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CollectionWithTransactions
import com.moneylytics.api.application.port.input.CreateCollectionUseCase
import com.moneylytics.api.application.port.input.DeleteCollectionUseCase
import com.moneylytics.api.application.port.input.GetCollectionsUseCase
import com.moneylytics.api.application.port.input.ManageCollectionMembersUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.UpdateCollectionUseCase
import com.moneylytics.api.domain.Collection
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import java.math.BigDecimal
import java.time.LocalDate

class CollectionControllerTest {
    private val userId = 1L
    private val resolveUserUseCase: ResolveUserUseCase = mock { on { resolveUser(any()) } doReturn userId }
    private val getCollectionsUseCase: GetCollectionsUseCase = mock()
    private val createCollectionUseCase: CreateCollectionUseCase = mock()
    private val updateCollectionUseCase: UpdateCollectionUseCase = mock()
    private val deleteCollectionUseCase: DeleteCollectionUseCase = mock()
    private val manageCollectionMembersUseCase: ManageCollectionMembersUseCase = mock()
    private val controller =
        CollectionController(
            getCollectionsUseCase,
            createCollectionUseCase,
            updateCollectionUseCase,
            deleteCollectionUseCase,
            manageCollectionMembersUseCase,
            resolveUserUseCase,
        )
    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()
    private val date = LocalDate.of(2025, 1, 15)

    @Test
    fun `should return 404 when getCollection returns null`() =
        runTest {
            whenever(getCollectionsUseCase.getCollection(99L, userId)).thenReturn(null)

            val response = controller.getCollection(99L, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should return 200 with collection data when found`() =
        runTest {
            val collection =
                CollectionWithTransactions(
                    id = 1L,
                    name = "Urlaub",
                    note = "Sommer 2025",
                    transactions = listOf(tx(10L)),
                )
            whenever(getCollectionsUseCase.getCollection(1L, userId)).thenReturn(collection)

            val response = controller.getCollection(1L, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            val body = response.body as CollectionDto
            assertThat(body.id).isEqualTo(1L)
            assertThat(body.name).isEqualTo("Urlaub")
            assertThat(body.note).isEqualTo("Sommer 2025")
            assertThat(body.transactions).hasSize(1)
        }

    @Test
    fun `should map all collections to DTOs`() =
        runTest {
            whenever(getCollectionsUseCase.getCollections(userId)).thenReturn(
                listOf(
                    CollectionWithTransactions(id = 1L, name = "Urlaub", note = null, transactions = emptyList()),
                    CollectionWithTransactions(id = 2L, name = "Klamotten", note = "H&M", transactions = listOf(tx(10L))),
                ),
            )

            val response = controller.listCollections(principal)

            assertThat(response.collections).hasSize(2)
            assertThat(response.collections[0].name).isEqualTo("Urlaub")
            assertThat(response.collections[1].transactions).hasSize(1)
        }

    @Test
    fun `should create collection and return empty transactions DTO`() =
        runTest {
            val saved = Collection(id = 5L, name = "Neu", note = null)
            whenever(createCollectionUseCase.createCollection(any(), any())).thenReturn(saved)

            val result = controller.createCollection(CreateCollectionRequest(name = "Neu"), principal)

            assertThat(result.id).isEqualTo(5L)
            assertThat(result.name).isEqualTo("Neu")
            assertThat(result.transactions).isEmpty()
        }

    @Test
    fun `should return 204 on deleteCollection`() =
        runTest {
            val response = controller.deleteCollection(3L, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            verify(deleteCollectionUseCase).deleteCollection(3L, userId)
        }

    @Test
    fun `should return 204 on addTransaction`() =
        runTest {
            val response = controller.addTransaction(1L, CollectionTransactionRequest(transactionId = 10L), principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            verify(manageCollectionMembersUseCase).addTransaction(1L, 10L, userId)
        }

    private fun tx(id: Long) =
        Transaction(
            id = id,
            category = null,
            subcategory = null,
            bookingDate = date,
            valueDate = date,
            accountingDate = date,
            amount = BigDecimal("-50.00"),
            currency = "EUR",
            accountIban = "DE00TEST",
        )
}
