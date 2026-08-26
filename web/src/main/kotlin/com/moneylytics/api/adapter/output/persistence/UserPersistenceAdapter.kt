package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.Role
import com.moneylytics.api.domain.User
import com.moneylytics.api.domain.UserSettings
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserPersistenceAdapter(
    private val jpaRepository: UserJpaRepository,
    private val accountJpaRepository: AccountJpaRepository,
    private val tierJpaRepository: CustomerTierJpaRepository,
) : UserRepository {
    override fun findByExternalId(externalId: String): User? = jpaRepository.findByExternalId(externalId)?.toDomain()

    override fun findById(id: Long): User? = jpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional
    override fun save(
        externalId: String,
        passwordHash: String?,
    ): User {
        val defaultTier =
            tierJpaRepository.findByIsDefaultTrue()
                ?: error("No default tier configured — run Flyway migration V38")
        val entity =
            jpaRepository
                .findByExternalId(externalId)
                ?.also { it.passwordHash = passwordHash }
                ?: UserEntity(externalId = externalId, passwordHash = passwordHash, tier = defaultTier)
        return jpaRepository.save(entity).toDomain()
    }

    override fun findAll(): List<User> = jpaRepository.findAll().map { it.toDomain() }

    override fun getSettings(userId: Long): UserSettings {
        val entity = jpaRepository.getReferenceById(userId)
        return entity.toSettings()
    }

    @Transactional
    override fun updateSettings(
        userId: Long,
        organizationId: Long,
        defaultAccountIban: String?,
        language: String?,
        transactionsColumnOrder: List<String>?,
    ): UserSettings {
        val entity = jpaRepository.getReferenceById(userId)
        entity.defaultAccount = defaultAccountIban?.let { accountJpaRepository.findByIbanAndOrganizationId(it, organizationId) }
        entity.language = language
        entity.transactionsColumnOrder = transactionsColumnOrder?.joinToString(",")
        return jpaRepository.save(entity).toSettings()
    }

    @Transactional
    override fun promoteToSystemAdmin(userId: Long) {
        jpaRepository.getReferenceById(userId).role = Role.SYSTEM_ADMIN
    }

    private fun UserEntity.toDomain() =
        User(id = id!!, externalId = externalId, passwordHash = passwordHash, role = role, tier = tier.toDomain())

    private fun UserEntity.toSettings() =
        UserSettings(
            defaultAccountIban = defaultAccount?.iban,
            language = language,
            transactionsColumnOrder = transactionsColumnOrder?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() },
        )
}
