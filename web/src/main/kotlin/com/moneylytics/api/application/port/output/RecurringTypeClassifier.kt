package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.ClassifierFeatures
import com.moneylytics.api.domain.RecurringType

interface RecurringTypeClassifier {
    fun classify(
        organizationId: Long,
        features: ClassifierFeatures,
    ): RecurringType

    fun train(
        organizationId: Long,
        type: RecurringType,
        features: ClassifierFeatures,
    )

    fun seedIfEmpty(organizationId: Long)
}
