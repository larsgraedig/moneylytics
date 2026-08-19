package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.TierRepository
import com.moneylytics.api.domain.Tier
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

internal fun TierEntity.toDomain() =
    Tier(
        id = id!!,
        name = name,
        description = description,
        active = active,
        isDefault = isDefault,
    )

@Component
class TierPersistenceAdapter(
    private val jpaRepository: TierJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : TierRepository {
    override fun findAll(): List<Tier> = jpaRepository.findAll().map { it.toDomain() }

    override fun findById(id: Long): Tier? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findDefault(): Tier =
        jpaRepository.findByIsDefaultTrue()?.toDomain()
            ?: error("No default tier configured")

    override fun save(tier: Tier): Tier {
        val entity =
            TierEntity(
                name = tier.name,
                description = tier.description,
                active = tier.active,
                isDefault = tier.isDefault,
            )
        return jpaRepository.save(entity).toDomain()
    }

    @Transactional
    override fun setDefault(tierId: Long) {
        jpaRepository.clearDefault()
        val entity = jpaRepository.getReferenceById(tierId)
        entity.isDefault = true
        jpaRepository.save(entity)
    }

    override fun findByUserId(userId: Long): Tier {
        val user = userJpaRepository.getReferenceById(userId)
        return user.tier.toDomain()
    }

    @Transactional
    override fun assignToUser(
        userId: Long,
        tierId: Long,
    ) {
        val user = userJpaRepository.getReferenceById(userId)
        val tier = jpaRepository.getReferenceById(tierId)
        user.tier = tier
    }
}
