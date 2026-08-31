package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.CategoryClassifierFeatures
import com.moneylytics.api.domain.TransactionsImportedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class CategorySuggestionService(
    private val transactionRepository: TransactionRepository,
    private val categoryClassifier: CategoryClassifier,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onTransactionsImported(event: TransactionsImportedEvent) {
        val transactions =
            transactionRepository
                .findByIdsAndOrganizationId(
                    ids = event.importedIds.toSet(),
                    organizationId = event.organizationId,
                ).filter { it.categoryId == null && !it.excludeFromSuggestions }

        if (transactions.isEmpty()) return

        val features =
            transactions.map { tx ->
                CategoryClassifierFeatures(
                    purpose = tx.purpose,
                    counterpartyName = tx.counterpartyName,
                    counterpartyIban = tx.counterpartyIban,
                    amount = tx.amount,
                )
            }
        val suggestions = categoryClassifier.suggestAll(event.organizationId, features)

        val updates =
            transactions.zip(suggestions).mapNotNull { (tx, categoryId) ->
                if (categoryId != null) requireNotNull(tx.id) to categoryId else null
            }

        if (updates.isNotEmpty()) {
            transactionRepository.updateSuggestedCategoryIds(updates)
        }
    }
}
