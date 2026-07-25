package com.moneylytics.api.domain

enum class RecurrenceCadence { WEEKLY, MONTHLY, QUARTERLY, SEMIANNUAL, YEARLY }

enum class RecurringType { SALARY, RENT, INSURANCE, SUBSCRIPTION, UTILITY, LOAN, MEMBERSHIP, OTHER }

enum class RecurrenceDirection { EXPENSE, INCOME }

enum class RecurrenceStatus { DETECTED, MANUAL }

enum class RecurrenceDeviation { ON_TRACK, AMOUNT_CHANGED, DATE_SHIFTED, OVERDUE }
