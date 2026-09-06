package com.moneylytics.api.config

import com.moneylytics.api.application.port.input.AssignTierToUserUseCase
import com.moneylytics.api.application.port.input.AssignTransactionToBudgetUseCase
import com.moneylytics.api.application.port.input.CreateBudgetUseCase
import com.moneylytics.api.application.port.input.CreateCollectionUseCase
import com.moneylytics.api.application.port.input.CreateOrganizationUseCase
import com.moneylytics.api.application.port.input.CreateTierUseCase
import com.moneylytics.api.application.port.input.CreateUserUseCase
import com.moneylytics.api.application.port.input.DetectRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ManageCollectionMembersUseCase
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.input.SaveThresholdUseCase
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.InvoiceRepository
import com.moneylytics.api.application.port.output.StripeCustomerRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.Role
import com.moneylytics.api.domain.Tier
import com.moneylytics.api.domain.User
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.boot.ApplicationArguments

class LocalDataInitializerTest {
    private val importTransactionsUseCase: ImportTransactionsUseCase = mock()
    private val createUserUseCase: CreateUserUseCase = mock()
    private val createOrganizationUseCase: CreateOrganizationUseCase = mock()
    private val userRepository: UserRepository = mock()
    private val getTransactionsUseCase: GetTransactionsUseCase = mock()
    private val manageTransactionOffsetUseCase: ManageTransactionOffsetUseCase = mock()
    private val saveThresholdUseCase: SaveThresholdUseCase = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val createBudgetUseCase: CreateBudgetUseCase = mock()
    private val assignTransactionToBudgetUseCase: AssignTransactionToBudgetUseCase = mock()
    private val createCollectionUseCase: CreateCollectionUseCase = mock()
    private val manageCollectionMembersUseCase: ManageCollectionMembersUseCase = mock()
    private val detectRecurringSeriesUseCase: DetectRecurringSeriesUseCase = mock()
    private val assignTierToUserUseCase: AssignTierToUserUseCase = mock()
    private val createTierUseCase: CreateTierUseCase = mock()
    private val stripeCustomerRepository: StripeCustomerRepository = mock()
    private val invoiceRepository: InvoiceRepository = mock()
    private val transactionRepository: TransactionRepository = mock()

    private val initializer =
        LocalDataInitializer(
            importTransactionsUseCase,
            createUserUseCase,
            createOrganizationUseCase,
            userRepository,
            getTransactionsUseCase,
            manageTransactionOffsetUseCase,
            saveThresholdUseCase,
            categoryRepository,
            createBudgetUseCase,
            assignTransactionToBudgetUseCase,
            createCollectionUseCase,
            manageCollectionMembersUseCase,
            detectRecurringSeriesUseCase,
            assignTierToUserUseCase,
            createTierUseCase,
            stripeCustomerRepository,
            invoiceRepository,
            transactionRepository,
        )

    @Test
    fun `should skip seeding when database already contains users`() {
        val existingTier = Tier(id = 1L, name = "Standard", description = null, active = true, isDefault = true)
        whenever(userRepository.findAll())
            .thenReturn(listOf(User(id = 1L, externalId = "existing@prod.dev", passwordHash = null, role = Role.USER, tier = existingTier)))

        initializer.run(mock<ApplicationArguments>())

        verifyNoInteractions(createTierUseCase, createUserUseCase, createOrganizationUseCase, importTransactionsUseCase)
    }

    @Test
    fun `should start seeding when database is empty`() {
        whenever(userRepository.findAll()).thenReturn(emptyList())

        // Downstream seed steps aren't stubbed here (they're pre-existing, unchanged logic) and will throw once
        // reached — this only asserts that the empty-database guard lets seeding begin.
        runCatching { initializer.run(mock<ApplicationArguments>()) }

        verify(createTierUseCase).createTier("Standard", "Standard Tier", isDefault = true)
    }
}
