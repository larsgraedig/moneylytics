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
    override fun save(externalId: String): User = jpaRepository.save(UserEntity(externalId = externalId)).toDomain()

    override fun findAll(): List<User> = jpaRepository.findAll().map { it.toDomain() }

    private fun UserEntity.toDomain() = User(id = id!!, externalId = externalId)
}
