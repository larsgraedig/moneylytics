package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.AssignTierToUserUseCase
import com.moneylytics.api.application.port.input.CreateTierUseCase
import com.moneylytics.api.application.port.input.GetUserTierUseCase
import com.moneylytics.api.application.port.input.ListTiersUseCase
import com.moneylytics.api.application.port.input.SetTierStripePriceUseCase
import com.moneylytics.api.application.port.output.TierRepository
import com.moneylytics.api.domain.Tier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class TierNotFoundException(
    tierId: Long,
) : RuntimeException("Tier $tierId not found")

@Service
class TierService(
    private val tierRepository: TierRepository,
) : CreateTierUseCase,
    ListTiersUseCase,
    AssignTierToUserUseCase,
    GetUserTierUseCase,
    SetTierStripePriceUseCase {
    override fun listTiers(): List<Tier> = tierRepository.findAll()

    override fun getUserTier(userId: Long): Tier = tierRepository.findByUserId(userId)

    @Transactional
    override fun createTier(
        name: String,
        description: String?,
        isDefault: Boolean,
    ): Tier {
        val saved = tierRepository.save(Tier(id = 0, name = name, description = description, active = true, isDefault = isDefault))
        if (isDefault) tierRepository.setDefault(saved.id)
        return saved
    }

    @Transactional
    override fun assignTierToUser(
        userId: Long,
        tierId: Long,
    ) {
        tierRepository.findById(tierId) ?: throw TierNotFoundException(tierId)
        tierRepository.assignToUser(userId, tierId)
    }

    @Transactional
    override fun setStripePrice(
        tierId: Long,
        priceId: String?,
    ) {
        tierRepository.findById(tierId) ?: throw TierNotFoundException(tierId)
        tierRepository.setStripePrice(tierId, priceId)
    }
}
