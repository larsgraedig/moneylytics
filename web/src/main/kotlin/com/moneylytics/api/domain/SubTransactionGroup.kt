package com.moneylytics.api.domain

data class SubTransactionGroup(
    val parent: Transaction,
    val children: List<Transaction>,
)
