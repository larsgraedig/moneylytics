package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.User
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserPersistenceAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserRepository {
    override fun findByExternalId(externalId: String): User? = jpaRepository.findByExternalId(externalId)?.toDomain()

    @Transactional
    override fun save(
        externalId: String,
        passwordHash: String?,
    ): User {
        val entity =
            jpaRepository
                .findByExternalId(externalId)
                ?.also { it.passwordHash = passwordHash }
                ?: UserEntity(externalId = externalId, passwordHash = passwordHash)
        return jpaRepository.save(entity).toDomain()
    }

    override fun findAll(): List<User> = jpaRepository.findAll().map { it.toDomain() }

    private fun UserEntity.toDomain() = User(id = id!!, externalId = externalId, passwordHash = passwordHash)
}
