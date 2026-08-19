package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Tier

interface ListTiersUseCase {
    fun listTiers(): List<Tier>
}
