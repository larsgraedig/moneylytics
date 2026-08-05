package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Account
import com.moneylytics.api.domain.Category
import com.moneylytics.api.domain.CategoryClassifierFeatures
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Service

@Service
class TransactionImportService(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryClassifier: CategoryClassifier,
) : ImportTransactionsUseCase {
    override fun importTransactions(command: ImportTransactionsCommand): Int {
        command.accountNames.forEach { (iban, name) ->
            if (accountRepository.findByIban(iban, command.organizationId) == null) {
                accountRepository.save(Account(iban = iban, name = name), command.organizationId)
            }
        }
        val count = transactionRepository.saveAll(command.transactions, command.organizationId)
        command.accountBalances.forEach { (iban, balance) ->
            accountRepository.updateBalance(iban, command.organizationId, balance.amount, balance.date)
        }
        trainOnImportedCategories(command)
        return count
    }

    private fun trainOnImportedCategories(command: ImportTransactionsCommand) {
        val categorized = command.transactions.filter { !it.category.isNullOrBlank() }
        if (categorized.isEmpty()) return

        val allCategories = categoryRepository.findAll(command.organizationId)
        val examples =
            categorized.mapNotNull { tx ->
                val categoryId = findLeafCategoryId(tx, allCategories) ?: return@mapNotNull null
                val features =
                    CategoryClassifierFeatures(
                        purpose = tx.purpose,
                        counterpartyName = tx.counterpartyName,
                        counterpartyIban = tx.counterpartyIban,
                        amount = tx.amount,
                    )
                if (features.purpose == null && features.counterpartyName == null && features.counterpartyIban == null) {
                    return@mapNotNull null
                }
                categoryId to features
            }
        if (examples.isNotEmpty()) {
            categoryClassifier.trainAll(command.organizationId, examples)
        }
    }

    private fun findLeafCategoryId(
        tx: Transaction,
        allCategories: List<Category>,
    ): Long? {
        if (tx.category.isNullOrBlank()) return null
        val childrenByParent = allCategories.groupBy { it.parentId }

        val root = allCategories.firstOrNull { it.parentId == null && it.name == tx.category } ?: return null
        if (tx.subcategory.isNullOrBlank() && tx.group.isNullOrBlank()) return root.id

        val level2 =
            childrenByParent[root.id]?.firstOrNull { it.name == tx.subcategory }
                ?: return root.id
        if (tx.group.isNullOrBlank()) return level2.id

        return childrenByParent[level2.id]?.firstOrNull { it.name == tx.group }?.id ?: level2.id
    }
}
