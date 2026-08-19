package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [ServiceLayerTestConfig::class])
@Transactional
abstract class AbstractServiceIT {
    @PersistenceContext protected lateinit var em: EntityManager

    @Autowired protected lateinit var userRepo: UserJpaRepository

    @Autowired protected lateinit var tierRepo: TierJpaRepository

    @Autowired protected lateinit var organizationRepo: OrganizationJpaRepository

    @Autowired protected lateinit var accountRepo: AccountJpaRepository

    protected lateinit var defaultTier: TierEntity
    protected lateinit var user: UserEntity
    protected lateinit var otherUser: UserEntity
    protected lateinit var organization: OrganizationEntity
    protected lateinit var otherOrganization: OrganizationEntity
    protected lateinit var account: AccountEntity

    protected val organizationId: Long get() = checkNotNull(organization.id)
    protected val otherOrganizationId: Long get() = checkNotNull(otherOrganization.id)

    @BeforeEach
    fun setUpBaseEntities() {
        defaultTier = tierRepo.save(TierEntity(name = "Standard", isDefault = true))
        user = userRepo.save(UserEntity(externalId = "test-user-1", tier = defaultTier))
        otherUser = userRepo.save(UserEntity(externalId = "test-user-2", tier = defaultTier))
        organization = organizationRepo.save(OrganizationEntity(name = "Test Org 1"))
        otherOrganization = organizationRepo.save(OrganizationEntity(name = "Test Org 2"))
        account = accountRepo.save(AccountEntity(iban = "DE00TEST000000000001", name = "Girokonto", organization = organization))
    }

    protected fun flushAndClear() {
        em.flush()
        em.clear()
    }
}
