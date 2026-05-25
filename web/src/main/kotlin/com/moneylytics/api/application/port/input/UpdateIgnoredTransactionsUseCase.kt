package com.moneylytics.api.application.port.input

fun interface UpdateIgnoredTransactionsUseCase {
    fun update(
        toIgnore: Collection<String>,
        toUnignore: Collection<String>,
        userId: Long,
    )
}
