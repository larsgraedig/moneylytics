package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/categories")
class CategoryController(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    @GetMapping
    suspend fun getCategories(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): CategoriesResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val grouped =
            withContext(Dispatchers.IO) {
                getCategoriesUseCase
                    .getCategories(organizationId)
                    .groupBy { it.name }
                    .map { (name, cats) ->
                        val withSub = cats.filter { it.subcategory != null }
                        val withoutSub = cats.filter { it.subcategory == null }
                        val subcategories =
                            withSub
                                .groupBy { it.subcategory!! }
                                .map { (subName, subCats) ->
                                    CategorySubGroupResponse(
                                        name = subName,
                                        groups =
                                            subCats
                                                .map {
                                                    CategoryLeafResponse(
                                                        id = requireNotNull(it.id),
                                                        name = it.group,
                                                    )
                                                }.sortedBy { it.name },
                                    )
                                }.sortedBy { it.name }
                        CategoryGroupResponse(
                            name = name,
                            subcategories = subcategories,
                            directGroups =
                                withoutSub
                                    .map {
                                        CategoryLeafResponse(
                                            id = requireNotNull(it.id),
                                            name = it.group,
                                        )
                                    }.sortedBy { it.name },
                        )
                    }.sortedBy { it.name }
            }
        return CategoriesResponse(categories = grouped)
    }
}
