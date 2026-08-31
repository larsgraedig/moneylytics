package com.moneylytics.api.application.port.input

fun interface SuggestCategoriesUseCase {
    fun suggestForOrganization(organizationId: Long)
}
