package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
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
        @AuthenticationPrincipal principal: UserDetails,
    ): CategoriesResponse {
        val grouped =
            withContext(Dispatchers.IO) {
                val userId = resolveUserUseCase.resolveUser(principal.username)
                getCategoriesUseCase
                    .getCategories(userId)
                    .groupBy { it.name }
                    .map { (name, cats) ->
                        val withGroup = cats.filter { it.group != null }
                        val withoutGroup = cats.filter { it.group == null }
                        val groups =
                            withGroup
                                .groupBy { it.group!! }
                                .map { (groupName, groupCats) ->
                                    CategorySubGroupResponse(
                                        name = groupName,
                                        subcategories = groupCats.map { it.subcategory }.sorted(),
                                    )
                                }.sortedBy { it.name }
                        CategoryGroupResponse(
                            name = name,
                            groups = groups,
                            subcategories = withoutGroup.map { it.subcategory }.sorted(),
                        )
                    }.sortedBy { it.name }
            }
        return CategoriesResponse(categories = grouped)
    }
}
