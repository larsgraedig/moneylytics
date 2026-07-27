package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [JpaRepositoryTestConfig::class])
@Transactional
abstract class AbstractJpaRepositoryIT {
    @PersistenceContext protected lateinit var em: EntityManager

    @Autowired protected lateinit var userRepo: UserJpaRepository

    @Autowired protected lateinit var organizationRepo: OrganizationJpaRepository

    @Autowired protected lateinit var accountRepo: AccountJpaRepository

    @Autowired protected lateinit var transactionRepo: TransactionJpaRepository

    protected lateinit var user: UserEntity
    protected lateinit var otherUser: UserEntity
    protected lateinit var organization: OrganizationEntity
    protected lateinit var otherOrganization: OrganizationEntity
    protected lateinit var account: AccountEntity

    protected val organizationId: Long get() = checkNotNull(organization.id)
    protected val otherOrganizationId: Long get() = checkNotNull(otherOrganization.id)

    @BeforeEach
    fun setUpBaseEntities() {
        user = userRepo.save(UserEntity(externalId = "test-user-1"))
        otherUser = userRepo.save(UserEntity(externalId = "test-user-2"))
        organization = organizationRepo.save(OrganizationEntity(name = "Test Org 1"))
        otherOrganization = organizationRepo.save(OrganizationEntity(name = "Test Org 2"))
        account = accountRepo.save(AccountEntity(iban = "DE00TEST000000000001", name = "Girokonto", organization = organization))
    }

    protected fun savedTransaction(
        fingerprint: String,
        category: String? = "Lebensmittel",
        subcategory: String? = "Supermarkt",
        accountingDate: LocalDate = LocalDate.of(2025, 1, 15),
        amount: BigDecimal = BigDecimal("-25.00"),
        forAccount: AccountEntity = account,
        forOrganization: OrganizationEntity = organization,
    ): TransactionEntity =
        transactionRepo.save(
            TransactionEntity(
                category = category,
                subcategory = subcategory,
                bookingDate = accountingDate,
                valueDate = accountingDate,
                accountingDate = accountingDate,
                amount = amount,
                currency = "EUR",
                account = forAccount,
                fingerprint = fingerprint,
                organization = forOrganization,
            ),
        )

    protected fun flushAndClear() {
        em.flush()
        em.clear()
    }
}
