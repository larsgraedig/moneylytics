package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.ClassifierFeatures
import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurringType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class NaiveBayesClassifierAdapterTest {
    private val tokenCountRepo: RecurringClassifierTokenCountJpaRepository = mock()
    private val classCountRepo: RecurringClassifierClassCountJpaRepository = mock()
    private val adapter = NaiveBayesClassifierAdapter(tokenCountRepo, classCountRepo)

    private val organizationId = 1L

    @Test
    fun `should classify using trained token weights`() {
        // All types have equal baseline; SALARY additionally has strong signal tokens
        val allTypes = RecurringType.entries
        val tokenRows =
            allTypes.map { type ->
                RecurringClassifierTokenCountEntity(organizationId, type.name, "baseline_token", 10)
            } +
                listOf(
                    RecurringClassifierTokenCountEntity(organizationId, "SALARY", "gehalt", 50),
                    RecurringClassifierTokenCountEntity(organizationId, "SALARY", "direction_INCOME", 50),
                )
        val classRows =
            allTypes.map { type ->
                val extra = if (type == RecurringType.SALARY) 100 else 0
                RecurringClassifierClassCountEntity(organizationId, type.name, 10 + extra)
            }
        whenever(tokenCountRepo.findByOrganizationId(organizationId)).thenReturn(tokenRows)
        whenever(classCountRepo.findByOrganizationId(organizationId)).thenReturn(classRows)

        val features =
            ClassifierFeatures(
                label = "Gehalt",
                cadence = RecurrenceCadence.MONTHLY,
                direction = RecurrenceDirection.INCOME,
                expectedAmount = BigDecimal("3500"),
            )

        val result = adapter.classify(organizationId, features)

        assertThat(result).isEqualTo(RecurringType.SALARY)
    }

    @Test
    fun `should return OTHER when no training data exists`() {
        whenever(tokenCountRepo.findByOrganizationId(organizationId)).thenReturn(emptyList())
        whenever(classCountRepo.findByOrganizationId(organizationId)).thenReturn(emptyList())

        val features =
            ClassifierFeatures(
                label = "Unbekannt",
                cadence = RecurrenceCadence.MONTHLY,
                direction = RecurrenceDirection.EXPENSE,
                expectedAmount = BigDecimal("25"),
            )

        val result = adapter.classify(organizationId, features)

        assertThat(result).isEqualTo(RecurringType.OTHER)
    }

    @Test
    fun `should increment class count and token counts when training`() {
        val features =
            ClassifierFeatures(
                label = "Netflix",
                cadence = RecurrenceCadence.MONTHLY,
                direction = RecurrenceDirection.EXPENSE,
                expectedAmount = BigDecimal("15"),
            )
        whenever(classCountRepo.findByOrganizationIdAndType(organizationId, "SUBSCRIPTION")).thenReturn(null)
        whenever(tokenCountRepo.findByOrganizationIdAndTypeAndToken(any(), any(), any())).thenReturn(null)
        whenever(classCountRepo.save(any())).thenAnswer { it.arguments[0] }
        whenever(tokenCountRepo.save(any())).thenAnswer { it.arguments[0] }

        adapter.train(organizationId, RecurringType.SUBSCRIPTION, features)

        val classCaptor = argumentCaptor<RecurringClassifierClassCountEntity>()
        verify(classCountRepo).save(classCaptor.capture())
        assertThat(classCaptor.firstValue.type).isEqualTo("SUBSCRIPTION")
        assertThat(classCaptor.firstValue.count).isEqualTo(1)
    }

    @Test
    fun `should skip seeding when model already has data for user`() {
        whenever(tokenCountRepo.existsByOrganizationId(organizationId)).thenReturn(true)

        adapter.seedIfEmpty(organizationId)

        verify(tokenCountRepo, never()).save(any())
    }

    @Test
    fun `should insert seed tokens for all known types when model is empty`() {
        whenever(tokenCountRepo.existsByOrganizationId(organizationId)).thenReturn(false)
        whenever(tokenCountRepo.save(any())).thenAnswer { it.arguments[0] }
        whenever(classCountRepo.save(any())).thenAnswer { it.arguments[0] }

        adapter.seedIfEmpty(organizationId)

        val tokenCaptor = argumentCaptor<RecurringClassifierTokenCountEntity>()
        verify(tokenCountRepo, org.mockito.kotlin.atLeast(1)).save(tokenCaptor.capture())

        val savedTypes = tokenCaptor.allValues.map { it.type }.toSet()
        assertThat(savedTypes).containsAll(listOf("SALARY", "RENT", "INSURANCE", "SUBSCRIPTION", "UTILITY", "LOAN"))
    }
}
