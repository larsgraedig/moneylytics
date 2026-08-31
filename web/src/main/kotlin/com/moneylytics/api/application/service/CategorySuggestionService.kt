package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.AcceptSuggestionUseCase
import com.moneylytics.api.application.port.input.RejectSuggestionUseCase
import com.moneylytics.api.application.port.input.SuggestCategoriesUseCase
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.CategorizationRequestedEvent
import com.moneylytics.api.domain.CategoryClassifierFeatures
import com.moneylytics.api.domain.Transaction
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class CategorySuggestionService(
    private val transactionRepository: TransactionRepository,
    private val categoryClassifier: CategoryClassifier,
) : SuggestCategoriesUseCase,
    AcceptSuggestionUseCase,
    RejectSuggestionUseCase {
    @EventListener
    fun onCategorizationRequested(event: CategorizationRequestedEvent) {
        suggestForOrganization(event.organizationId)
    }

    override fun suggestForOrganization(organizationId: Long) {
        val transactions = transactionRepository.findUncategorizedForSuggestion(organizationId)
        if (transactions.isEmpty()) return
        val features = transactions.map { it.toFeatures() }
        val suggestions = categoryClassifier.suggestAll(organizationId, features)
        transactions.zip(suggestions).forEach { (tx, suggestedCategoryId) ->
            if (suggestedCategoryId != null) {
                transactionRepository.updateSuggestedCategory(
                    id = requireNotNull(tx.id),
                    organizationId = organizationId,
                    suggestedCategoryId = suggestedCategoryId,
                )
            }
        }
        val count = suggestions.count { it != null }
        logger.info { "Generated $count category suggestion(s) for organization $organizationId" }
    }

    override fun accept(
        transactionId: Long,
        organizationId: Long,
    ): Transaction? = transactionRepository.acceptSuggestion(transactionId, organizationId)

    override fun reject(
        transactionId: Long,
        organizationId: Long,
    ): Transaction? = transactionRepository.rejectSuggestion(transactionId, organizationId)

    private fun Transaction.toFeatures() =
        CategoryClassifierFeatures(
            purpose = purpose,
            counterpartyName = counterpartyName,
            counterpartyIban = counterpartyIban,
            amount = amount,
        )
}
