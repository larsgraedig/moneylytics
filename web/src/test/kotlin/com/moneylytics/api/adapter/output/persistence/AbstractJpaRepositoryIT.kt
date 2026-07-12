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

    @Autowired protected lateinit var accountRepo: AccountJpaRepository

    @Autowired protected lateinit var transactionRepo: TransactionJpaRepository

    protected lateinit var user: UserEntity
    protected lateinit var otherUser: UserEntity
    protected lateinit var account: AccountEntity

    protected val userId: Long get() = checkNotNull(user.id)
    protected val otherUserId: Long get() = checkNotNull(otherUser.id)

    @BeforeEach
    fun setUpBaseEntities() {
        user = userRepo.save(UserEntity(externalId = "test-user-1"))
        otherUser = userRepo.save(UserEntity(externalId = "test-user-2"))
        account = accountRepo.save(AccountEntity(iban = "DE00TEST000000000001", name = "Girokonto", user = user))
    }

    protected fun savedTransaction(
        fingerprint: String,
        category: String? = "Lebensmittel",
        subcategory: String? = "Supermarkt",
        accountingDate: LocalDate = LocalDate.of(2025, 1, 15),
        amount: BigDecimal = BigDecimal("-25.00"),
        forAccount: AccountEntity = account,
        forUser: UserEntity = user,
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
                user = forUser,
            ),
        )

    protected fun flushAndClear() {
        em.flush()
        em.clear()
    }
}
