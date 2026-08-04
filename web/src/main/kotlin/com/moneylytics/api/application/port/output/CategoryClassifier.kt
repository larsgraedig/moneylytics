package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.CategoryClassifierFeatures

interface CategoryClassifier {
    fun suggestAll(
        organizationId: Long,
        features: List<CategoryClassifierFeatures>,
    ): List<Long?>

    fun train(
        organizationId: Long,
        categoryId: Long,
        features: CategoryClassifierFeatures,
    )

    fun trainAll(
        organizationId: Long,
        examples: List<Pair<Long, CategoryClassifierFeatures>>,
    )
}
