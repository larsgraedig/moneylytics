package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.CategoryClassifierFeatures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class NaiveBayesCategoryClassifierAdapterTest {
    private val classCountRepo: CategoryClassifierClassCountJpaRepository = mock()
    private val tokenCountRepo: CategoryClassifierTokenCountJpaRepository = mock()
    private val transactionJpaRepository: TransactionJpaRepository = mock()
    private val adapter = NaiveBayesCategoryClassifierAdapter(classCountRepo, tokenCountRepo, transactionJpaRepository)

    private val organizationId = 1L
    private val groceriesCategoryId = 10L
    private val transportCategoryId = 20L

    @Test
    fun `should suggest category when one class dominates tokens`() {
        whenever(classCountRepo.existsByOrganizationId(organizationId)).thenReturn(true)

        val classRows =
            listOf(
                CategoryClassifierClassCountEntity(organizationId, groceriesCategoryId, 100),
                CategoryClassifierClassCountEntity(organizationId, transportCategoryId, 100),
            )
        val tokenRows =
            listOf(
                CategoryClassifierTokenCountEntity(organizationId, groceriesCategoryId, "rewe", 80),
                CategoryClassifierTokenCountEntity(organizationId, groceriesCategoryId, "supermarkt", 40),
                CategoryClassifierTokenCountEntity(organizationId, transportCategoryId, "bvg", 60),
                CategoryClassifierTokenCountEntity(organizationId, transportCategoryId, "ticket", 30),
            )
        whenever(classCountRepo.findByOrganizationId(organizationId)).thenReturn(classRows)
        whenever(tokenCountRepo.findByOrganizationId(organizationId)).thenReturn(tokenRows)

        val features =
            CategoryClassifierFeatures(
                purpose = "REWE Supermarkt Berlin",
                counterpartyName = null,
                counterpartyIban = null,
            )

        val result = adapter.suggestAll(organizationId, listOf(features))

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(groceriesCategoryId)
    }

    @Test
    fun `should return null when no training data exists`() {
        whenever(classCountRepo.existsByOrganizationId(organizationId)).thenReturn(false)
        whenever(transactionJpaRepository.findCategorizedForBootstrap(organizationId)).thenReturn(emptyList())
        whenever(classCountRepo.findByOrganizationId(organizationId)).thenReturn(emptyList())
        whenever(tokenCountRepo.findByOrganizationId(organizationId)).thenReturn(emptyList())

        val features =
            CategoryClassifierFeatures(
                purpose = "Unbekannte Transaktion",
                counterpartyName = null,
                counterpartyIban = null,
            )

        val result = adapter.suggestAll(organizationId, listOf(features))

        assertThat(result).hasSize(1)
        assertThat(result[0]).isNull()
    }

    @Test
    fun `should return null when confidence is too low`() {
        whenever(classCountRepo.existsByOrganizationId(organizationId)).thenReturn(true)

        val classRows =
            listOf(
                CategoryClassifierClassCountEntity(organizationId, groceriesCategoryId, 10),
                CategoryClassifierClassCountEntity(organizationId, transportCategoryId, 10),
            )
        val tokenRows =
            listOf(
                CategoryClassifierTokenCountEntity(organizationId, groceriesCategoryId, "zahlung", 5),
                CategoryClassifierTokenCountEntity(organizationId, transportCategoryId, "zahlung", 5),
            )
        whenever(classCountRepo.findByOrganizationId(organizationId)).thenReturn(classRows)
        whenever(tokenCountRepo.findByOrganizationId(organizationId)).thenReturn(tokenRows)

        val features =
            CategoryClassifierFeatures(
                purpose = "Zahlung",
                counterpartyName = null,
                counterpartyIban = null,
            )

        val result = adapter.suggestAll(organizationId, listOf(features))

        assertThat(result[0]).isNull()
    }

    @Test
    fun `should return null when features have no classifiable signal`() {
        whenever(classCountRepo.existsByOrganizationId(organizationId)).thenReturn(true)
        whenever(classCountRepo.findByOrganizationId(organizationId)).thenReturn(
            listOf(CategoryClassifierClassCountEntity(organizationId, groceriesCategoryId, 10)),
        )
        whenever(tokenCountRepo.findByOrganizationId(organizationId)).thenReturn(emptyList())

        val features =
            CategoryClassifierFeatures(
                purpose = null,
                counterpartyName = null,
                counterpartyIban = null,
            )

        val result = adapter.suggestAll(organizationId, listOf(features))

        assertThat(result[0]).isNull()
    }

    @Test
    fun `should strongly prefer counterpartyIban match`() {
        whenever(classCountRepo.existsByOrganizationId(organizationId)).thenReturn(true)
        val iban = "DE89370400440532013000"

        val classRows =
            listOf(
                CategoryClassifierClassCountEntity(organizationId, groceriesCategoryId, 50),
                CategoryClassifierClassCountEntity(organizationId, transportCategoryId, 50),
            )
        val tokenRows =
            listOf(
                CategoryClassifierTokenCountEntity(organizationId, groceriesCategoryId, "iban_$iban", 45),
                CategoryClassifierTokenCountEntity(organizationId, transportCategoryId, "bvg", 40),
            )
        whenever(classCountRepo.findByOrganizationId(organizationId)).thenReturn(classRows)
        whenever(tokenCountRepo.findByOrganizationId(organizationId)).thenReturn(tokenRows)

        val features =
            CategoryClassifierFeatures(
                purpose = null,
                counterpartyName = null,
                counterpartyIban = iban,
            )

        val result = adapter.suggestAll(organizationId, listOf(features))

        assertThat(result[0]).isEqualTo(groceriesCategoryId)
    }

    @Test
    fun `should increment class and token counts on train`() {
        whenever(classCountRepo.findByOrganizationIdAndCategoryId(organizationId, groceriesCategoryId)).thenReturn(null)
        whenever(tokenCountRepo.findByOrganizationIdAndCategoryIdAndToken(any(), any(), any())).thenReturn(null)
        whenever(classCountRepo.save(any())).thenAnswer { it.arguments[0] }
        whenever(tokenCountRepo.save(any())).thenAnswer { it.arguments[0] }

        adapter.train(
            organizationId,
            groceriesCategoryId,
            CategoryClassifierFeatures(
                purpose = "REWE Markt",
                counterpartyName = null,
                counterpartyIban = null,
            ),
        )

        val classCaptor = argumentCaptor<CategoryClassifierClassCountEntity>()
        verify(classCountRepo).save(classCaptor.capture())
        assertThat(classCaptor.firstValue.categoryId).isEqualTo(groceriesCategoryId)
        assertThat(classCaptor.firstValue.count).isEqualTo(1)
    }

    @Test
    fun `should bootstrap from existing categorized transactions when empty`() {
        whenever(classCountRepo.existsByOrganizationId(organizationId)).thenReturn(false)
        val bootstrapRows: List<Array<out Any?>> =
            listOf(
                arrayOf("REWE Supermarkt", null, null, groceriesCategoryId, BigDecimal("-35.50")),
                arrayOf("BVG Ticket", null, null, transportCategoryId, BigDecimal("-2.80")),
            )
        whenever(transactionJpaRepository.findCategorizedForBootstrap(organizationId)).thenReturn(bootstrapRows)
        whenever(classCountRepo.saveAll(any<List<CategoryClassifierClassCountEntity>>())).thenReturn(emptyList())
        whenever(tokenCountRepo.saveAll(any<List<CategoryClassifierTokenCountEntity>>())).thenReturn(emptyList())
        whenever(classCountRepo.findByOrganizationId(organizationId)).thenReturn(emptyList())
        whenever(tokenCountRepo.findByOrganizationId(organizationId)).thenReturn(emptyList())

        adapter.suggestAll(organizationId, emptyList())

        val classCaptor = argumentCaptor<List<CategoryClassifierClassCountEntity>>()
        verify(classCountRepo).saveAll(classCaptor.capture())
        assertThat(classCaptor.firstValue).hasSize(2)
        assertThat(classCaptor.firstValue.map { it.categoryId }).containsExactlyInAnyOrder(
            groceriesCategoryId,
            transportCategoryId,
        )
    }

    @Test
    fun `should skip bootstrap when training data already exists`() {
        whenever(classCountRepo.existsByOrganizationId(organizationId)).thenReturn(true)
        whenever(classCountRepo.findByOrganizationId(organizationId)).thenReturn(emptyList())
        whenever(tokenCountRepo.findByOrganizationId(organizationId)).thenReturn(emptyList())

        adapter.suggestAll(organizationId, emptyList())

        verify(transactionJpaRepository, never()).findCategorizedForBootstrap(any())
    }

    @Test
    fun `should include amount sign and range tokens when amount is provided`() {
        whenever(classCountRepo.findByOrganizationIdAndCategoryId(organizationId, groceriesCategoryId)).thenReturn(null)
        whenever(tokenCountRepo.findByOrganizationIdAndCategoryIdAndToken(any(), any(), any())).thenReturn(null)
        whenever(classCountRepo.save(any())).thenAnswer { it.arguments[0] }
        whenever(tokenCountRepo.save(any())).thenAnswer { it.arguments[0] }

        adapter.train(
            organizationId,
            groceriesCategoryId,
            CategoryClassifierFeatures(
                purpose = "REWE Markt",
                counterpartyName = null,
                counterpartyIban = null,
                amount = BigDecimal("-42.50"),
            ),
        )

        val tokenCaptor = argumentCaptor<CategoryClassifierTokenCountEntity>()
        verify(tokenCountRepo, atLeast(1)).save(tokenCaptor.capture())
        val tokens = tokenCaptor.allValues.map { it.token }
        assertThat(tokens).contains("amt_sign_neg", "amt_100")
    }

    @Test
    fun `should include positive sign token when amount is positive`() {
        whenever(classCountRepo.findByOrganizationIdAndCategoryId(organizationId, transportCategoryId)).thenReturn(null)
        whenever(tokenCountRepo.findByOrganizationIdAndCategoryIdAndToken(any(), any(), any())).thenReturn(null)
        whenever(classCountRepo.save(any())).thenAnswer { it.arguments[0] }
        whenever(tokenCountRepo.save(any())).thenAnswer { it.arguments[0] }

        adapter.train(
            organizationId,
            transportCategoryId,
            CategoryClassifierFeatures(
                purpose = "Gehalt",
                counterpartyName = null,
                counterpartyIban = null,
                amount = BigDecimal("2500.00"),
            ),
        )

        val tokenCaptor = argumentCaptor<CategoryClassifierTokenCountEntity>()
        verify(tokenCountRepo, atLeast(1)).save(tokenCaptor.capture())
        val tokens = tokenCaptor.allValues.map { it.token }
        assertThat(tokens).contains("amt_sign_pos", "amt_inf")
    }
}
