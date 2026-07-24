package com.moneylytics.api.application.service

import com.moneylytics.api.domain.Transaction
import java.math.BigDecimal

private const val MAX_PURPOSE_WORDS = 5

internal fun groupKey(tx: Transaction): String {
    val direction = if (tx.amount < BigDecimal.ZERO) "E" else "I"
    val identifier =
        tx.counterpartyIban
            ?: tx.counterpartyName?.let { normalizeName(it) }
            ?: tx.purpose?.let { normalizePurpose(it) }
            ?: "unknown"
    return "${tx.accountIban}|$direction|$identifier"
}

internal fun normalizeName(name: String): String =
    name
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun normalizePurpose(purpose: String): String {
    var s = purpose.trim().lowercase()
    s = s.replace(Regex("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b"), "")
    s = s.replace(Regex("\\b\\d+[.,]\\d+\\b"), "")
    s = s.replace(Regex("\\b[a-z]{0,2}\\d{4,}\\b"), "")
    s = s.replace(Regex("[^a-z0-9 ]"), " ")
    s = s.replace(Regex("\\s+"), " ").trim()
    return s
        .split(" ")
        .filter { it.length > 2 }
        .take(MAX_PURPOSE_WORDS)
        .joinToString(" ")
}
