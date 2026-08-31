package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.service.CategorySuggestionService
import com.moneylytics.api.domain.CategoryClassifierFeatures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

class CategorySuggestionIT : AbstractServiceIT() {
    @Autowired private lateinit var categorySuggestionService: CategorySuggestionService

    @Autowired private lateinit var categoryClassifier: CategoryClassifier

    @Autowired private lateinit var transactionJpaRepository: TransactionJpaRepository

    @Autowired private lateinit var categoryJpaRepository: CategoryJpaRepository

    @Test
    fun `should store suggested category id on uncategorized transaction`() {
        val category = categoryJpaRepository.save(CategoryEntity(name = "Streaming", parent = null, organization = organization))
        val categoryId = requireNotNull(category.id)
        val entity =
            transactionJpaRepository.save(
                TransactionEntity(
                    bookingDate = LocalDate.of(2025, 1, 1),
                    valueDate = LocalDate.of(2025, 1, 1),
                    accountingDate = LocalDate.of(2025, 1, 1),
                    amount = BigDecimal("-12.99"),
                    currency = "EUR",
                    account = account,
                    organization = organization,
                    fingerprint = "fp-suggest-test",
                    purpose = "Netflix Subscription",
                ),
            )
        flushAndClear()

        whenever(categoryClassifier.suggestAll(eq(organizationId), any<List<CategoryClassifierFeatures>>()))
            .thenReturn(listOf(categoryId))

        categorySuggestionService.suggestForOrganization(organizationId)
        flushAndClear()

        val updated = requireNotNull(transactionJpaRepository.findById(requireNotNull(entity.id)).orElse(null))
        assertThat(updated.suggestedCategory?.id).isEqualTo(categoryId)
        assertThat(updated.excludeFromSuggestions).isFalse()
    }

    @Test
    fun `should accept suggestion and apply it as real category`() {
        val category = categoryJpaRepository.save(CategoryEntity(name = "Lebensmittel", parent = null, organization = organization))
        val categoryId = requireNotNull(category.id)
        val entity =
            transactionJpaRepository.save(
                TransactionEntity(
                    bookingDate = LocalDate.of(2025, 1, 1),
                    valueDate = LocalDate.of(2025, 1, 1),
                    accountingDate = LocalDate.of(2025, 1, 1),
                    amount = BigDecimal("-50.00"),
                    currency = "EUR",
                    account = account,
                    organization = organization,
                    fingerprint = "fp-accept-test",
                    suggestedCategory = category,
                ),
            )
        flushAndClear()

        categorySuggestionService.accept(requireNotNull(entity.id), organizationId)
        flushAndClear()

        val updated = requireNotNull(transactionJpaRepository.findById(requireNotNull(entity.id)).orElse(null))
        assertThat(updated.category?.id).isEqualTo(categoryId)
        assertThat(updated.suggestedCategory).isNull()
        assertThat(updated.excludeFromSuggestions).isFalse()
    }

    @Test
    fun `should reject suggestion and set excludeFromSuggestions`() {
        val category = categoryJpaRepository.save(CategoryEntity(name = "Versicherung", parent = null, organization = organization))
        val entity =
            transactionJpaRepository.save(
                TransactionEntity(
                    bookingDate = LocalDate.of(2025, 1, 1),
                    valueDate = LocalDate.of(2025, 1, 1),
                    accountingDate = LocalDate.of(2025, 1, 1),
                    amount = BigDecimal("-100.00"),
                    currency = "EUR",
                    account = account,
                    organization = organization,
                    fingerprint = "fp-reject-test",
                    suggestedCategory = category,
                ),
            )
        flushAndClear()

        categorySuggestionService.reject(requireNotNull(entity.id), organizationId)
        flushAndClear()

        val updated = requireNotNull(transactionJpaRepository.findById(requireNotNull(entity.id)).orElse(null))
        assertThat(updated.suggestedCategory).isNull()
        assertThat(updated.excludeFromSuggestions).isTrue()
        assertThat(updated.category).isNull()
    }

    @Test
    fun `should not suggest for transactions with excludeFromSuggestions true`() {
        transactionJpaRepository.save(
            TransactionEntity(
                bookingDate = LocalDate.of(2025, 1, 1),
                valueDate = LocalDate.of(2025, 1, 1),
                accountingDate = LocalDate.of(2025, 1, 1),
                amount = BigDecimal("-25.00"),
                currency = "EUR",
                account = account,
                organization = organization,
                fingerprint = "fp-excluded-test",
                excludeFromSuggestions = true,
            ),
        )
        flushAndClear()

        val uncategorized = transactionJpaRepository.findUncategorizedForSuggestion(organizationId)

        assertThat(uncategorized).isEmpty()
    }
}
