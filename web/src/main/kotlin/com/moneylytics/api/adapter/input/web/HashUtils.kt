package com.moneylytics.api.adapter.input.web

import java.security.MessageDigest

internal fun sha256(bytes: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
