package com.moneylytics.api.adapter.input.web

import java.security.MessageDigest

fun assignFingerprints(rows: List<ParsedRawRow>): Map<Int, String> {
    val counts = mutableMapOf<String, Int>()
    return rows.associate { row ->
        val raw =
            "${row.accountIban}|${row.bookingDate}|${row.valueDate}|" +
                "${requireNotNull(row.amount).stripTrailingZeros().toPlainString()}|${row.currency}"
        val n = (counts.getOrDefault(raw, 0) + 1).also { counts[raw] = it }
        val fp = sha256(if (n == 1) raw else "$raw:${n - 1}")
        row.rowNumber to fp
    }
}

fun sha256(raw: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
