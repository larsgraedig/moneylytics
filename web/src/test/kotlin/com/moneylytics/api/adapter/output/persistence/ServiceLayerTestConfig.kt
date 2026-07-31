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
    fun ignoredTransactionPersistenceAdapter(
        jpaRepository: IgnoredTransactionJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
    ): IgnoredTransactionPersistenceAdapter = IgnoredTransactionPersistenceAdapter(jpaRepository, organizationJpaRepository)

    @Bean
    fun categoryPersistenceAdapter(
        jpaRepository: CategoryJpaRepository,
        organizationJpaRepository: OrganizationJpaRepository,
    ): CategoryPersistenceAdapter = CategoryPersistenceAdapter(jpaRepository, organizationJpaRepository)

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
