package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.BulkCategoryUpdate
import com.moneylytics.api.application.port.input.BulkUpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.BurnRateResponse
import com.moneylytics.api.application.port.input.CashflowResponse
import com.moneylytics.api.application.port.input.CategoryTotalsResponse
import com.moneylytics.api.application.port.input.GetBurnRateQuery
import com.moneylytics.api.application.port.input.GetBurnRateUseCase
import com.moneylytics.api.application.port.input.GetCashflowQuery
import com.moneylytics.api.application.port.input.GetCashflowUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.GetCategoryTotalsQuery
import com.moneylytics.api.application.port.input.GetCategoryTotalsUseCase
import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.Granularity
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.TransactionType
import com.moneylytics.api.application.port.input.UpdateTransactionAccountingDateUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCommentUseCase
import com.moneylytics.api.application.port.input.bucketKey
import com.moneylytics.api.application.port.input.generateBuckets
import com.moneylytics.api.domain.Category
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.time.LocalDate

data class BulkUpdateCategoryRequestDto(
    val updates: List<BulkCategoryUpdateDto>,
)

data class BulkCategoryUpdateDto(
    val id: Long,
    val categoryId: Long?,
)

data class UpdateCategoryRequest(
    val categoryId: Long?,
)

data class UpdateCommentRequest(
    val comment: String?,
)

data class UpdateAccountingDateRequest(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val accountingDate: LocalDate,
)

@RestController
@RequestMapping("/transactions")
class TransactionQueryController(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getCashflowUseCase: GetCashflowUseCase,
    private val getBurnRateUseCase: GetBurnRateUseCase,
    private val getCategoryTotalsUseCase: GetCategoryTotalsUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
    private val updateTransactionCategoryUseCase: UpdateTransactionCategoryUseCase,
    private val updateTransactionCommentUseCase: UpdateTransactionCommentUseCase,
    private val updateTransactionAccountingDateUseCase: UpdateTransactionAccountingDateUseCase,
    private val bulkUpdateTransactionCategoryUseCase: BulkUpdateTransactionCategoryUseCase,
) {
    companion object {
        private const val SERIES_SPEC_PARTS_WITH_SUB = 3
    }

    @GetMapping("/sankey")
    suspend fun getSankeyData(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) accountId: Long? = null,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): SankeyResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val (transactions, categories) =
            withContext(Dispatchers.IO) {
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, organizationId, type = TransactionType.EXPENSES, accountId = accountId),
                ) to getCategoriesUseCase.getCategories(organizationId)
            }
        return transactions.toSankeyResponse(categories)
    }

    @GetMapping("/list")
    suspend fun listTransactions(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) subcategory: String? = null,
        @RequestParam(required = false) group: String? = null,
        @RequestParam(required = false) accountId: Long? = null,
        @RequestParam(required = false) type: TransactionType = TransactionType.ALL,
        @RequestParam(required = false) uncategorized: Boolean = false,
        @RequestParam(required = false) excludeCollectionId: Long? = null,
        @RequestParam(required = false) excludeBudgetId: Long? = null,
        @RequestParam(required = false) categoryId: Long? = null,
        @RequestParam(required = false) limit: Int? = null,
        @RequestParam(required = false, defaultValue = "0") offset: Int = 0,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): TransactionListResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val transactions =
            withContext(Dispatchers.IO) {
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(
                        from = from,
                        to = to,
                        organizationId = organizationId,
                        type = type,
                        accountId = accountId,
                        category = category,
                        subcategory = subcategory,
                        group = group,
                        uncategorized = uncategorized,
                        excludeCollectionId = excludeCollectionId,
                        excludeBudgetId = excludeBudgetId,
                        categoryId = categoryId,
                    ),
                )
            }
        val sorted = transactions.sortedByDescending { it.accountingDate }
        val total = sorted.sumOf { it.effectiveAmount() }
        val page = if (limit != null) sorted.drop(offset).take(limit) else sorted.drop(offset)
        return TransactionListResponse(
            transactions = page.map { it.toItem() },
            total = total,
            totalCount = sorted.size,
            hasMore = limit != null && offset + page.size < sorted.size,
        )
    }

    @PatchMapping("/{id}")
    suspend fun updateTransactionCategory(
        @PathVariable id: Long,
        @RequestBody request: UpdateCategoryRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<TransactionItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            val updated = updateTransactionCategoryUseCase.updateCategory(id, organizationId, request.categoryId)
            if (updated != null) ResponseEntity.ok(updated.toItem()) else ResponseEntity.notFound().build()
        }
    }

    @PatchMapping("/{id}/accounting-date")
    suspend fun updateTransactionAccountingDate(
        @PathVariable id: Long,
        @RequestBody request: UpdateAccountingDateRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<TransactionItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            val updated = updateTransactionAccountingDateUseCase.updateAccountingDate(id, organizationId, request.accountingDate)
            if (updated != null) ResponseEntity.ok(updated.toItem()) else ResponseEntity.notFound().build()
        }
    }

    @PatchMapping("/{id}/comment")
    suspend fun updateTransactionComment(
        @PathVariable id: Long,
        @RequestBody request: UpdateCommentRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<TransactionItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            val updated = updateTransactionCommentUseCase.updateComment(id, organizationId, request.comment)
            if (updated != null) ResponseEntity.ok(updated.toItem()) else ResponseEntity.notFound().build()
        }
    }

    @PatchMapping("/bulk")
    suspend fun bulkUpdateTransactionCategory(
        @RequestBody request: BulkUpdateCategoryRequestDto,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): List<TransactionItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            bulkUpdateTransactionCategoryUseCase
                .bulkUpdateCategory(
                    request.updates.map { BulkCategoryUpdate(it.id, it.categoryId) },
                    organizationId,
                ).map { it.toItem() }
        }
    }

    @GetMapping("/trends")
    suspend fun getTrends(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) series: List<String>? = null,
        @RequestParam(required = false) granularity: Granularity = Granularity.MONTHLY,
        @RequestParam(required = false) accountId: Long? = null,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): TrendsResponse {
        val effectiveSeries = series ?: emptyList()
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val buckets = generateBuckets(from, to, granularity)

        val groups =
            effectiveSeries.map { spec ->
                if (spec.startsWith("id:")) {
                    buildIdBasedTrendGroup(
                        categoryId = spec.removePrefix("id:").toLong(),
                        from = from,
                        to = to,
                        organizationId = organizationId,
                        accountId = accountId,
                        buckets = buckets,
                        granularity = granularity,
                    )
                } else {
                    buildStringBasedTrendGroup(
                        spec = spec,
                        from = from,
                        to = to,
                        organizationId = organizationId,
                        accountId = accountId,
                        buckets = buckets,
                        granularity = granularity,
                    )
                }
            }

        return TrendsResponse(granularity = granularity, buckets = buckets, groups = groups)
    }

    private fun bucketSums(
        txns: List<Transaction>,
        buckets: List<String>,
        granularity: Granularity,
    ): List<BigDecimal> {
        val byBucket = txns.groupBy { bucketKey(it.accountingDate, granularity) }
        return buckets.map { bucket -> byBucket[bucket]?.sumOf { it.effectiveAmount().abs() } ?: BigDecimal.ZERO }
    }

    private suspend fun buildIdBasedTrendGroup(
        categoryId: Long,
        from: LocalDate,
        to: LocalDate,
        organizationId: Long,
        accountId: Long?,
        buckets: List<String>,
        granularity: Granularity,
    ): TrendSeriesGroup {
        val allCategories = withContext(Dispatchers.IO) { getCategoriesUseCase.getCategories(organizationId) }
        val allTransactions =
            withContext(Dispatchers.IO) {
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(
                        from = from,
                        to = to,
                        organizationId = organizationId,
                        type = TransactionType.EXPENSES,
                        accountId = accountId,
                        categoryId = categoryId,
                    ),
                )
            }
        val categoryName = allCategories.find { it.id == categoryId }?.name ?: "?"
        val directChildren = allCategories.filter { it.parentId == categoryId }
        val mainEntry =
            TrendSeriesEntry(
                label = categoryName,
                data = bucketSums(allTransactions, buckets, granularity),
                role = SeriesRole.MAIN_SELECTED,
                categoryId = categoryId,
            )
        val subEntries =
            directChildren.mapNotNull { child ->
                val childId = child.id ?: return@mapNotNull null
                val childSubtreeIds = collectSubtreeIds(childId, allCategories)
                val childTxns = allTransactions.filter { it.categoryId in childSubtreeIds }
                val sums = bucketSums(childTxns, buckets, granularity)
                if (sums.all { it == BigDecimal.ZERO }) return@mapNotNull null
                TrendSeriesEntry(
                    label = child.name,
                    data = sums,
                    role = SeriesRole.SUB_CONTEXT,
                    categoryId = childId,
                )
            }
        return TrendSeriesGroup(main = mainEntry, subs = subEntries)
    }

    private suspend fun buildStringBasedTrendGroup(
        spec: String,
        from: LocalDate,
        to: LocalDate,
        organizationId: Long,
        accountId: Long?,
        buckets: List<String>,
        granularity: Granularity,
    ): TrendSeriesGroup {
        val parts = spec.split(":")
        val category = parts[0]
        val selectedSub: String?
        val selectedGroup: String?
        when (parts.size) {
            SERIES_SPEC_PARTS_WITH_SUB -> {
                selectedSub = parts[1].ifEmpty { null }
                selectedGroup = parts[2].ifEmpty { null }
            }
            2 -> {
                selectedSub = null
                selectedGroup = parts[1].ifEmpty { null }
            }
            else -> {
                selectedSub = null
                selectedGroup = null
            }
        }
        val allTransactions =
            withContext(Dispatchers.IO) {
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(
                        from = from,
                        to = to,
                        organizationId = organizationId,
                        type = TransactionType.EXPENSES,
                        accountId = accountId,
                        category = category,
                        subcategory = selectedSub,
                    ),
                )
            }
        val mainLabel = if (selectedSub != null) selectedSub else category
        val hasGroupSelection = selectedGroup != null
        val mainEntry =
            TrendSeriesEntry(
                label = mainLabel,
                data = bucketSums(allTransactions, buckets, granularity),
                role = if (hasGroupSelection) SeriesRole.MAIN_CONTEXT else SeriesRole.MAIN_SELECTED,
            )
        val subEntries =
            allTransactions
                .groupBy { it.group }
                .entries
                .sortedBy { it.key }
                .map { (groupName, txns) ->
                    TrendSeriesEntry(
                        label = groupName,
                        data = bucketSums(txns, buckets, granularity),
                        role = if (groupName == selectedGroup) SeriesRole.SUB_SELECTED else SeriesRole.SUB_CONTEXT,
                    )
                }
        return TrendSeriesGroup(main = mainEntry, subs = subEntries)
    }

    private fun collectSubtreeIds(
        rootId: Long,
        all: List<Category>,
    ): Set<Long> {
        val childrenByParent = all.groupBy { it.parentId }
        val result = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            result.add(id)
            childrenByParent[id]?.forEach { queue.add(requireNotNull(it.id)) }
        }
        return result
    }

    @GetMapping("/cashflow")
    suspend fun getCashflow(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) granularity: Granularity = Granularity.MONTHLY,
        @RequestParam(required = false) accountId: Long? = null,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): CashflowResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            getCashflowUseCase.getCashflow(
                GetCashflowQuery(
                    from = from,
                    to = to,
                    organizationId = organizationId,
                    granularity = granularity,
                    accountId = accountId,
                ),
            )
        }
    }

    @GetMapping("/category-totals")
    suspend fun getCategoryTotals(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) accountId: Long? = null,
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) categoryId: Long? = null,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): CategoryTotalsResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            getCategoryTotalsUseCase.getCategoryTotals(
                GetCategoryTotalsQuery(
                    from = from,
                    to = to,
                    organizationId = organizationId,
                    accountId = accountId,
                    category = category,
                    categoryId = categoryId,
                ),
            )
        }
    }

    @GetMapping("/burnrate")
    suspend fun getBurnRate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) accountId: Long? = null,
        @RequestParam(required = false) rollingWindow: Int = 7,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): BurnRateResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            getBurnRateUseCase.getBurnRate(
                GetBurnRateQuery(
                    from = from,
                    to = to,
                    organizationId = organizationId,
                    accountId = accountId,
                    rollingWindow = rollingWindow,
                ),
            )
        }
    }

    private fun List<Transaction>.toSankeyResponse(categories: List<Category>): SankeyResponse {
        val categoryById = categories.associateBy { requireNotNull(it.id) }

        fun pathFromRoot(leafId: Long): List<Category> {
            val path = ArrayDeque<Category>()
            var current: Category? = categoryById[leafId]
            while (current != null) {
                path.addFirst(current)
                current = current.parentId?.let { categoryById[it] }
            }
            return path.toList()
        }

        val nodeIndex = linkedMapOf<Long, Int>()

        fun indexFor(id: Long) = nodeIndex.getOrPut(id) { nodeIndex.size }

        val linkAmounts = mutableMapOf<Pair<Long, Long>, BigDecimal>()

        for (tx in this) {
            val catId = tx.categoryId ?: continue
            val path = pathFromRoot(catId)
            path.forEach { indexFor(requireNotNull(it.id)) }
            for (i in 0 until path.size - 1) {
                val parentId = requireNotNull(path[i].id)
                val childId = requireNotNull(path[i + 1].id)
                linkAmounts.merge(parentId to childId, tx.effectiveAmount().abs(), BigDecimal::add)
            }
        }

        val links =
            linkAmounts.map { (pair, amount) ->
                SankeyLink(
                    source = nodeIndex.getValue(pair.first),
                    target = nodeIndex.getValue(pair.second),
                    value = amount,
                )
            }

        val totals = mutableMapOf<Int, BigDecimal>()
        links.forEach { link ->
            totals.merge(link.source, link.value, BigDecimal::add)
            totals.merge(link.target, link.value, BigDecimal::add)
        }

        val nodes =
            nodeIndex.entries.sortedBy { it.value }.map { (catId, idx) ->
                val cat = requireNotNull(categoryById[catId])
                SankeyNode(
                    name = cat.name,
                    value = totals.getOrDefault(idx, BigDecimal.ZERO),
                    nodeKey = "id:$catId",
                    categoryId = catId,
                    namePath = pathFromRoot(catId).map { it.name },
                )
            }

        return SankeyResponse(nodes = nodes, links = links)
    }
}
