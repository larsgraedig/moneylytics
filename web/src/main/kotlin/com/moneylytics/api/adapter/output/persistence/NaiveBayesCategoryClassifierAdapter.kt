package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.domain.CategoryClassifierFeatures
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.math.exp
import kotlin.math.ln

@Component
class NaiveBayesCategoryClassifierAdapter(
    private val classCountRepo: CategoryClassifierClassCountJpaRepository,
    private val tokenCountRepo: CategoryClassifierTokenCountJpaRepository,
    private val transactionJpaRepository: TransactionJpaRepository,
) : CategoryClassifier {
    @Transactional
    override fun suggestAll(
        organizationId: Long,
        features: List<CategoryClassifierFeatures>,
    ): List<Long?> {
        bootstrapIfEmpty(organizationId)
        if (features.isEmpty()) return emptyList()

        val classCounts =
            classCountRepo
                .findByOrganizationId(organizationId)
                .associate { it.categoryId to it.count.toLong() }
        val totalExamples = classCounts.values.sum()
        if (totalExamples == 0L) return List(features.size) { null }

        val tokenCountRows = tokenCountRepo.findByOrganizationId(organizationId)
        val tokenCounts: Map<Long, Map<String, Long>> =
            tokenCountRows
                .groupBy { it.categoryId }
                .mapValues { (_, rows) -> rows.associate { it.token to it.count.toLong() } }
        val vocabularySize =
            tokenCountRows
                .map { it.token }
                .toSet()
                .size
                .toLong()
        val allClasses = classCounts.keys.toList()

        return features.map { f ->
            classify(f, allClasses, classCounts, tokenCounts, vocabularySize, totalExamples)
        }
    }

    @Transactional
    override fun train(
        organizationId: Long,
        categoryId: Long,
        features: CategoryClassifierFeatures,
    ) {
        val classEntity =
            classCountRepo
                .findByOrganizationIdAndCategoryId(organizationId, categoryId)
                ?: CategoryClassifierClassCountEntity(organizationId = organizationId, categoryId = categoryId, count = 0)
        classEntity.count += 1
        classCountRepo.save(classEntity)

        extractTokens(features).forEach { token ->
            val tokenEntity =
                tokenCountRepo
                    .findByOrganizationIdAndCategoryIdAndToken(organizationId, categoryId, token)
                    ?: CategoryClassifierTokenCountEntity(
                        organizationId = organizationId,
                        categoryId = categoryId,
                        token = token,
                        count = 0,
                    )
            tokenEntity.count += 1
            tokenCountRepo.save(tokenEntity)
        }
    }

    @Transactional
    override fun trainAll(
        organizationId: Long,
        examples: List<Pair<Long, CategoryClassifierFeatures>>,
    ) {
        if (examples.isEmpty()) return

        val classByCategory =
            classCountRepo
                .findByOrganizationId(organizationId)
                .associateBy { it.categoryId }
                .toMutableMap()
        val tokenByCategory =
            tokenCountRepo
                .findByOrganizationId(organizationId)
                .groupBy { it.categoryId }
                .mapValues { (_, rows) -> rows.associateBy { it.token }.toMutableMap() }
                .toMutableMap()

        examples.forEach { (categoryId, features) ->
            val classEntity =
                classByCategory.getOrPut(categoryId) {
                    CategoryClassifierClassCountEntity(
                        organizationId = organizationId,
                        categoryId = categoryId,
                        count = 0,
                    )
                }
            classEntity.count += 1

            val tc = tokenByCategory.getOrPut(categoryId) { mutableMapOf() }
            extractTokens(features).forEach { token ->
                val tokenEntity =
                    tc.getOrPut(token) {
                        CategoryClassifierTokenCountEntity(
                            organizationId = organizationId,
                            categoryId = categoryId,
                            token = token,
                            count = 0,
                        )
                    }
                tokenEntity.count += 1
            }
        }

        classCountRepo.saveAll(classByCategory.values.toList())
        tokenByCategory.values.forEach { tc -> tokenCountRepo.saveAll(tc.values.toList()) }
    }

    private fun bootstrapIfEmpty(organizationId: Long) {
        if (classCountRepo.existsByOrganizationId(organizationId)) return

        val txns = transactionJpaRepository.findCategorizedForBootstrap(organizationId)
        if (txns.isEmpty()) return

        val classCounts = mutableMapOf<Long, Int>()
        val tokenCounts = mutableMapOf<Long, MutableMap<String, Int>>()

        txns.forEach { row ->
            val categoryId = (row[3] as? Long) ?: return@forEach
            val features =
                CategoryClassifierFeatures(
                    purpose = row[0] as? String,
                    counterpartyName = row[1] as? String,
                    counterpartyIban = row[2] as? String,
                )
            classCounts.merge(categoryId, 1, Int::plus)
            val tc = tokenCounts.getOrPut(categoryId) { mutableMapOf() }
            extractTokens(features).forEach { token -> tc.merge(token, 1, Int::plus) }
        }

        classCountRepo.saveAll(
            classCounts.map { (catId, count) ->
                CategoryClassifierClassCountEntity(
                    organizationId = organizationId,
                    categoryId = catId,
                    count = count,
                )
            },
        )
        tokenCountRepo.saveAll(
            tokenCounts.flatMap { (catId, tokens) ->
                tokens.map { (token, count) ->
                    CategoryClassifierTokenCountEntity(
                        organizationId = organizationId,
                        categoryId = catId,
                        token = token,
                        count = count,
                    )
                }
            },
        )
    }

    private fun classify(
        features: CategoryClassifierFeatures,
        allClasses: List<Long>,
        classCounts: Map<Long, Long>,
        tokenCounts: Map<Long, Map<String, Long>>,
        vocabularySize: Long,
        totalExamples: Long,
    ): Long? {
        if (allClasses.isEmpty()) return null
        val tokens = extractTokens(features)
        if (tokens.isEmpty()) return null

        val scores =
            allClasses.map { categoryId ->
                val classCount = classCounts.getOrDefault(categoryId, 0L)
                val logPrior = ln((classCount + 1.0) / (totalExamples + allClasses.size))
                val typeTokens = tokenCounts.getOrDefault(categoryId, emptyMap())
                val totalTypeTokens = typeTokens.values.sum()
                val logLikelihood =
                    tokens.sumOf { token ->
                        val tokenCount = typeTokens.getOrDefault(token, 0L)
                        ln((tokenCount + 1.0) / (totalTypeTokens + vocabularySize + 1))
                    }
                categoryId to (logPrior + logLikelihood)
            }

        val maxScore = scores.maxOf { it.second }
        val expScores = scores.map { (cls, score) -> cls to exp(score - maxScore) }
        val sumExp = expScores.sumOf { it.second }
        val top = expScores.maxByOrNull { it.second } ?: return null
        val confidence = top.second / sumExp

        return if (confidence >= CONFIDENCE_THRESHOLD) top.first else null
    }

    private fun extractTokens(features: CategoryClassifierFeatures): List<String> {
        val textTokens =
            listOfNotNull(features.purpose, features.counterpartyName)
                .flatMap { text ->
                    text
                        .lowercase()
                        .replace(Regex("[^a-z0-9 ]"), " ")
                        .split(Regex("\\s+"))
                        .filter { it.length >= MIN_TOKEN_LENGTH && !it.all { c -> c.isDigit() } }
                }
        val ibanToken =
            features.counterpartyIban
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf("iban_$it") }
                ?: emptyList()
        return textTokens + ibanToken
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.65
        private const val MIN_TOKEN_LENGTH = 3
    }
}
