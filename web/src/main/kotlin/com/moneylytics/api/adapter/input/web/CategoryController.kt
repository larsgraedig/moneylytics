package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/categories")
class CategoryController(
    private val getCategoriesUseCase: GetCategoriesUseCase,
) {
    @GetMapping
    suspend fun getCategories(): CategoriesResponse {
        val grouped =
            getCategoriesUseCase
                .getCategories()
                .groupBy { it.name }
                .map { (name, cats) ->
                    CategoryGroupResponse(
                        name = name,
                        subcategories = cats.map { it.subcategory }.sorted(),
                    )
                }.sortedBy { it.name }
        return CategoriesResponse(categories = grouped)
    }
}
