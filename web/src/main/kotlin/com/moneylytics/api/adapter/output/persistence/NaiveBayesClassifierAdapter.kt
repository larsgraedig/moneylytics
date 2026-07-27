package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.RecurringTypeClassifier
import com.moneylytics.api.domain.ClassifierFeatures
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurringType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.math.ln

@Component
class NaiveBayesClassifierAdapter(
    private val tokenCountRepo: RecurringClassifierTokenCountJpaRepository,
    private val classCountRepo: RecurringClassifierClassCountJpaRepository,
) : RecurringTypeClassifier {
    @Transactional(readOnly = true)
    override fun classify(
        organizationId: Long,
        features: ClassifierFeatures,
    ): RecurringType {
        val classCounts = classCountRepo.findByOrganizationId(organizationId).associate { it.type to it.count.toLong() }
        val totalExamples = classCounts.values.sum()
        if (totalExamples == 0L) return RecurringType.OTHER

        val tokenCountRows = tokenCountRepo.findByOrganizationId(organizationId)
        val tokenCounts: Map<String, Map<String, Long>> =
            tokenCountRows
                .groupBy { it.type }
                .mapValues { (_, rows) -> rows.associate { it.token to it.count.toLong() } }

        val vocabularySize =
            tokenCountRows
                .map { it.token }
                .toSet()
                .size
                .toLong()
        val tokens = extractTokens(features)
        val allTypes = RecurringType.entries

        val scores =
            allTypes.map { type ->
                val classCount = classCounts.getOrDefault(type.name, 0L)
                val logPrior = ln((classCount + 1.0) / (totalExamples + allTypes.size))

                val typeTokens = tokenCounts.getOrDefault(type.name, emptyMap())
                val totalTypeTokens = typeTokens.values.sum()

                val logLikelihood =
                    tokens.sumOf { token ->
                        val tokenCount = typeTokens.getOrDefault(token, 0L)
                        ln((tokenCount + 1.0) / (totalTypeTokens + vocabularySize + 1))
                    }

                type to (logPrior + logLikelihood)
            }

        return scores.maxByOrNull { it.second }?.first ?: RecurringType.OTHER
    }

    @Transactional
    override fun train(
        organizationId: Long,
        type: RecurringType,
        features: ClassifierFeatures,
    ) {
        val classEntity =
            classCountRepo.findByOrganizationIdAndType(organizationId, type.name)
                ?: RecurringClassifierClassCountEntity(organizationId = organizationId, type = type.name, count = 0)
        classEntity.count += 1
        classCountRepo.save(classEntity)

        extractTokens(features).forEach { token ->
            val tokenEntity =
                tokenCountRepo.findByOrganizationIdAndTypeAndToken(organizationId, type.name, token)
                    ?: RecurringClassifierTokenCountEntity(organizationId = organizationId, type = type.name, token = token, count = 0)
            tokenEntity.count += 1
            tokenCountRepo.save(tokenEntity)
        }
    }

    @Transactional
    override fun seedIfEmpty(organizationId: Long) {
        if (tokenCountRepo.existsByOrganizationId(organizationId)) return
        SEED_DATA.forEach { (type, tokens) ->
            tokens.forEach { token ->
                val entity =
                    RecurringClassifierTokenCountEntity(
                        organizationId = organizationId,
                        type = type.name,
                        token = token,
                        count = SEED_COUNT,
                    )
                tokenCountRepo.save(entity)
            }
            val classEntity =
                RecurringClassifierClassCountEntity(
                    organizationId = organizationId,
                    type = type.name,
                    count =
                        tokens.size * SEED_COUNT,
                )
            classCountRepo.save(classEntity)
        }
    }

    private fun extractTokens(features: ClassifierFeatures): List<String> {
        val labelTokens =
            features.label
                .lowercase()
                .replace(Regex("[^a-z0-9 ]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= MIN_TOKEN_LENGTH }

        val cadenceToken = "cadence_${features.cadence.name}"
        val directionToken = "direction_${features.direction.name}"
        val amountToken =
            when {
                features.expectedAmount.abs() < BigDecimal("50") -> "amount_small"
                features.expectedAmount.abs() < BigDecimal("500") -> "amount_medium"
                else -> "amount_large"
            }

        return labelTokens + listOf(cadenceToken, directionToken, amountToken)
    }

    companion object {
        private const val SEED_COUNT = 10
        private const val MIN_TOKEN_LENGTH = 3

        val SEED_DATA: Map<RecurringType, List<String>> =
            mapOf(
                RecurringType.SALARY to
                    listOf(
                        "gehalt",
                        "lohn",
                        "bezuge",
                        "salary",
                        "payroll",
                        "entgelt",
                        "direction_${RecurrenceDirection.INCOME.name}",
                    ),
                RecurringType.RENT to
                    listOf(
                        "miete",
                        "rent",
                        "vermieter",
                        "wohnungsgeld",
                        "hausgeld",
                        "pacht",
                        "amount_large",
                    ),
                RecurringType.INSURANCE to
                    listOf(
                        "versicherung",
                        "insurance",
                        "allianz",
                        "axa",
                        "generali",
                        "huk",
                        "ergo",
                        "zurich",
                    ),
                RecurringType.LOAN to
                    listOf("kredit", "darlehen", "tilgung", "finanzierung", "hypothek", "amount_large"),
                RecurringType.UTILITY to
                    listOf(
                        "strom",
                        "gas",
                        "wasser",
                        "fernwarme",
                        "stadtwerke",
                        "enbw",
                        "rwe",
                        "telekom",
                        "vodafone",
                        "internet",
                    ),
                RecurringType.SUBSCRIPTION to
                    listOf(
                        "netflix",
                        "spotify",
                        "disney",
                        "apple",
                        "abo",
                        "subscription",
                        "mitglied",
                        "amount_small",
                    ),
                RecurringType.MEMBERSHIP to
                    listOf("verein", "mitgliedschaft", "club", "beitrag"),
            )
    }
}
