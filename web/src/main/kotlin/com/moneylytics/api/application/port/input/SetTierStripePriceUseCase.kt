package com.moneylytics.api.application.port.input

interface SetTierStripePriceUseCase {
    fun setStripePrice(
        tierId: Long,
        priceId: String?,
    )
}
