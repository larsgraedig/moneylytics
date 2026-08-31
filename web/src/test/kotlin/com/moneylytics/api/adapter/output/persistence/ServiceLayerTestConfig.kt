package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.application.port.output.TransactionImportIgnoredTransactionRepository
import com.moneylytics.api.application.port.output.TransactionImportRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.application.service.CategoryService
import com.moneylytics.api.application.service.CategorySuggestionService
import com.moneylytics.api.application.service.TransactionImportIgnoredTransactionService
import com.moneylytics.api.application.service.TransactionImportService
import com.moneylytics.api.domain.CategoryClassifierFeatures
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(JpaRepositoryTestConfig::class)
class ServiceLayerTestConfig {
    @Bean
    fun accountPersistenceAdapter(
        jpaRepository: AccountJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
    ): AccountPersistenceAdapter = AccountPersistenceAdapter(jpaRepository, organizationJpaRepository)

    @Bean
    fun transactionPersistenceAdapter(
        jpaRepository: TransactionJpaRepository,
        accountJpaRepository: AccountJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
        offsetJpaRepository: TransactionOffsetJpaRepository,
        groupMemberJpaRepository: TransactionGroupMemberJpaRepository,
        groupJpaRepository: TransactionGroupJpaRepository,
        collectionTransactionJpaRepository: CollectionTransactionJpaRepository,
        collectionJpaRepository: CollectionJpaRepository,
        budgetTransactionJpaRepository: BudgetTransactionJpaRepository,
        categoryJpaRepository: CategoryJpaRepository,
    ): TransactionPersistenceAdapter =
        TransactionPersistenceAdapter(
            jpaRepository,
            accountJpaRepository,
            organizationJpaRepository,
            offsetJpaRepository,
            groupMemberJpaRepository,
            groupJpaRepository,
            collectionTransactionJpaRepository,
            collectionJpaRepository,
            budgetTransactionJpaRepository,
            categoryJpaRepository,
        )

    @Bean
    fun transactionImportIgnoredTransactionPersistenceAdapter(
        jpaRepository: TransactionImportIgnoredTransactionJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
    ): TransactionImportIgnoredTransactionPersistenceAdapter =
        TransactionImportIgnoredTransactionPersistenceAdapter(jpaRepository, organizationJpaRepository)

    @Bean
    fun categoryPersistenceAdapter(
        jpaRepository: CategoryJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
    ): CategoryPersistenceAdapter = CategoryPersistenceAdapter(jpaRepository, organizationJpaRepository)

    @Bean
    fun categoryClassifier(): CategoryClassifier {
        val classifier: CategoryClassifier = mock()
        whenever(classifier.suggestAll(any(), any<List<CategoryClassifierFeatures>>())).thenReturn(emptyList())
        return classifier
    }

    @Bean
    fun transactionImportRepository(
        jpaRepository: TransactionImportJpaRepository,
        importFileJpaRepository: TransactionImportFileJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
    ): TransactionImportPersistenceAdapter =
        TransactionImportPersistenceAdapter(jpaRepository, importFileJpaRepository, organizationJpaRepository)

    @Bean
    fun transactionImportFileRepository(
        jpaRepository: TransactionImportFileJpaRepository,
        transactionImportJpaRepository: TransactionImportJpaRepository,
    ): TransactionImportFilePersistenceAdapter = TransactionImportFilePersistenceAdapter(jpaRepository, transactionImportJpaRepository)

    @Bean
    fun applicationEventPublisher(): ApplicationEventPublisher = mock()

    @Bean
    fun transactionImportService(
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        categoryClassifier: CategoryClassifier,
        transactionImportRepository: TransactionImportRepository,
        importFileRepository: com.moneylytics.api.application.port.output.TransactionImportFileRepository,
        eventPublisher: ApplicationEventPublisher,
    ): TransactionImportService =
        TransactionImportService(
            transactionRepository,
            accountRepository,
            categoryRepository,
            categoryClassifier,
            transactionImportRepository,
            importFileRepository,
            eventPublisher,
        )

    @Bean
    fun categorySuggestionService(
        transactionRepository: TransactionRepository,
        categoryClassifier: CategoryClassifier,
    ): CategorySuggestionService = CategorySuggestionService(transactionRepository, categoryClassifier)

    @Bean
    fun transactionImportIgnoredTransactionService(
        repository: TransactionImportIgnoredTransactionRepository,
    ): TransactionImportIgnoredTransactionService = TransactionImportIgnoredTransactionService(repository)

    @Bean
    fun thresholdRepository(): ThresholdRepository = mock()

    @Bean
    fun categoryService(
        categoryRepository: CategoryRepository,
        transactionRepository: TransactionRepository,
        thresholdRepository: ThresholdRepository,
    ): CategoryService = CategoryService(categoryRepository, transactionRepository, thresholdRepository)
}
