package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.ClassifierFeatures
import com.moneylytics.api.domain.RecurringType

interface RecurringTypeClassifier {
    fun classify(
        userId: Long,
        features: ClassifierFeatures,
    ): RecurringType

    fun train(
        userId: Long,
        type: RecurringType,
        features: ClassifierFeatures,
    )

    fun seedIfEmpty(userId: Long)
}
