package com.moneylytics.api.domain

enum class SubscriptionStatus {
    ACTIVE,
    TRIALING,
    INCOMPLETE,
    INCOMPLETE_EXPIRED,
    PAST_DUE,
    CANCELED,
    UNPAID,
    PAUSED,
    CANCELING,
}
