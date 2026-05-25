package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/categories")
class CategoryController(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @GetMapping
    suspend fun getCategories(
        @RequestHeader("X-User-Id") externalId: String,
    ): CategoriesResponse {
        val grouped =
            withContext(Dispatchers.IO) {
                val userId = resolveUserUseCase.resolveUser(externalId)
                getCategoriesUseCase
                    .getCategories(userId)
                    .groupBy { it.name }
                    .map { (name, cats) ->
                        CategoryGroupResponse(
                            name = name,
                            subcategories = cats.map { it.subcategory }.sorted(),
                        )
                    }.sortedBy { it.name }
            }
        return CategoriesResponse(categories = grouped)
    }
}
