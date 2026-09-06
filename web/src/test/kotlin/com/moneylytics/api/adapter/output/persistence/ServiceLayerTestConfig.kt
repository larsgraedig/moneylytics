package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.RecurringFalsePositiveRepository
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.RecurringSyncLogRepository
import com.moneylytics.api.application.port.output.RecurringTypeClassifier
import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.application.port.output.TransactionImportIgnoredTransactionRepository
import com.moneylytics.api.application.port.output.TransactionImportRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.application.service.CategoryService
import com.moneylytics.api.application.service.RecurringMatcherService
import com.moneylytics.api.application.service.RecurringSeriesDetector
import com.moneylytics.api.application.service.RecurringSeriesService
import com.moneylytics.api.application.service.TransactionImportIgnoredTransactionService
import com.moneylytics.api.application.service.TransactionImportService
import com.moneylytics.api.domain.CategoryClassifierFeatures
import com.moneylytics.api.domain.RecurringType
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
    fun transactionImportService(
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        categoryClassifier: CategoryClassifier,
        transactionImportRepository: TransactionImportRepository,
        importFileRepository: com.moneylytics.api.application.port.output.TransactionImportFileRepository,
    ): TransactionImportService =
        TransactionImportService(
            transactionRepository,
            accountRepository,
            categoryRepository,
            categoryClassifier,
            transactionImportRepository,
            importFileRepository,
            mock(),
        )

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

    @Bean
    fun recurringSeriesPersistenceAdapter(
        recurringSeriesJpaRepository: RecurringSeriesJpaRepository,
        recurringSeriesMemberJpaRepository: RecurringSeriesMemberJpaRepository,
        recurringExpectedOccurrenceJpaRepository: RecurringExpectedOccurrenceJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
        transactionJpaRepository: TransactionJpaRepository,
    ): RecurringSeriesPersistenceAdapter =
        RecurringSeriesPersistenceAdapter(
            recurringSeriesJpaRepository,
            recurringSeriesMemberJpaRepository,
            recurringExpectedOccurrenceJpaRepository,
            organizationJpaRepository,
            transactionJpaRepository,
        )

    @Bean
    fun recurringSyncLogRepository(
        syncLogJpaRepository: RecurringSyncLogJpaRepository,
        syncLogEntryJpaRepository: RecurringSyncLogEntryJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
    ): RecurringSyncLogRepository =
        RecurringSyncLogPersistenceAdapter(syncLogJpaRepository, syncLogEntryJpaRepository, organizationJpaRepository)

    @Bean
    fun recurringFalsePositiveRepository(
        jpaRepository: RecurringFalsePositiveJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
    ): RecurringFalsePositiveRepository = RecurringFalsePositivePersistenceAdapter(jpaRepository, organizationJpaRepository)

    @Bean
    fun recurringTypeClassifier(): RecurringTypeClassifier {
        val classifier: RecurringTypeClassifier = mock()
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        return classifier
    }

    @Bean
    fun recurringSeriesDetector(): RecurringSeriesDetector = RecurringSeriesDetector()

    @Bean
    fun recurringMatcherService(
        recurringSeriesRepository: RecurringSeriesRepository,
        transactionRepository: TransactionRepository,
        recurringSyncLogRepository: RecurringSyncLogRepository,
        accountRepository: AccountRepository,
    ): RecurringMatcherService =
        RecurringMatcherService(recurringSeriesRepository, transactionRepository, recurringSyncLogRepository, accountRepository)

    @Bean
    fun recurringSeriesService(
        transactionRepository: TransactionRepository,
        recurringSeriesRepository: RecurringSeriesRepository,
        recurringFalsePositiveRepository: RecurringFalsePositiveRepository,
        recurringSeriesDetector: RecurringSeriesDetector,
        recurringTypeClassifier: RecurringTypeClassifier,
        recurringSyncLogRepository: RecurringSyncLogRepository,
    ): RecurringSeriesService =
        RecurringSeriesService(
            transactionRepository,
            recurringSeriesRepository,
            recurringFalsePositiveRepository,
            recurringSeriesDetector,
            recurringTypeClassifier,
            recurringSyncLogRepository,
        )
}
