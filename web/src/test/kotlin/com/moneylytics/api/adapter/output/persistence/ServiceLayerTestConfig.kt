package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.IgnoredTransactionRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.application.service.CategoryService
import com.moneylytics.api.application.service.IgnoredTransactionService
import com.moneylytics.api.application.service.TransactionImportService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(JpaRepositoryTestConfig::class)
class ServiceLayerTestConfig {
    @Bean
    fun accountPersistenceAdapter(
        jpaRepository: AccountJpaRepository,
        userJpaRepository: UserJpaRepository,
    ): AccountPersistenceAdapter = AccountPersistenceAdapter(jpaRepository, userJpaRepository)

    @Bean
    fun transactionPersistenceAdapter(
        jpaRepository: TransactionJpaRepository,
        accountJpaRepository: AccountJpaRepository,
        userJpaRepository: UserJpaRepository,
        offsetJpaRepository: TransactionOffsetJpaRepository,
        groupMemberJpaRepository: TransactionGroupMemberJpaRepository,
        groupJpaRepository: TransactionGroupJpaRepository,
        collectionTransactionJpaRepository: CollectionTransactionJpaRepository,
        collectionJpaRepository: CollectionJpaRepository,
        budgetTransactionJpaRepository: BudgetTransactionJpaRepository,
    ): TransactionPersistenceAdapter =
        TransactionPersistenceAdapter(
            jpaRepository,
            accountJpaRepository,
            userJpaRepository,
            offsetJpaRepository,
            groupMemberJpaRepository,
            groupJpaRepository,
            collectionTransactionJpaRepository,
            collectionJpaRepository,
            budgetTransactionJpaRepository,
        )

    @Bean
    fun ignoredTransactionPersistenceAdapter(
        jpaRepository: IgnoredTransactionJpaRepository,
        userJpaRepository: UserJpaRepository,
    ): IgnoredTransactionPersistenceAdapter = IgnoredTransactionPersistenceAdapter(jpaRepository, userJpaRepository)

    @Bean
    fun categoryPersistenceAdapter(
        jpaRepository: CategoryJpaRepository,
        userJpaRepository: UserJpaRepository,
    ): CategoryPersistenceAdapter = CategoryPersistenceAdapter(jpaRepository, userJpaRepository)

    @Bean
    fun transactionImportService(
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
    ): TransactionImportService = TransactionImportService(transactionRepository, accountRepository)

    @Bean
    fun ignoredTransactionService(repository: IgnoredTransactionRepository): IgnoredTransactionService =
        IgnoredTransactionService(repository)

    @Bean
    fun categoryService(categoryRepository: CategoryRepository): CategoryService = CategoryService(categoryRepository)
}
