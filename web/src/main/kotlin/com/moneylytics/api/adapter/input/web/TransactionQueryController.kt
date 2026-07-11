package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.BurnRateResponse
import com.moneylytics.api.application.port.input.CashflowResponse
import com.moneylytics.api.application.port.input.GetBurnRateQuery
import com.moneylytics.api.application.port.input.GetBurnRateUseCase
import com.moneylytics.api.application.port.input.GetCashflowQuery
import com.moneylytics.api.application.port.input.GetCashflowUseCase
import com.moneylytics.api.application.port.input.GetCategoriesUseCase
import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.Granularity
import com.moneylytics.api.application.port.input.ResolveUserUseCase
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
import java.math.BigDecimal
import java.time.LocalDate

data class UpdateCategoryRequest(
    val category: String,
    val subcategory: String,
    val categoryGroup: String? = null,
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
    private val getCashflowUseCase: GetCashflowUseCase,
    private val getBurnRateUseCase: GetBurnRateUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
    private val updateTransactionCategoryUseCase: UpdateTransactionCategoryUseCase,
    private val updateTransactionCommentUseCase: UpdateTransactionCommentUseCase,
    private val updateTransactionAccountingDateUseCase: UpdateTransactionAccountingDateUseCase,
) {
    @GetMapping("/sankey")
    suspend fun getSankeyData(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) iban: String? = null,
        @AuthenticationPrincipal principal: UserDetails,
    ): SankeyResponse {
        val (transactions, groupLookup) =
            withContext(Dispatchers.IO) {
                val userId = resolveUserUseCase.resolveUser(principal.username)
                val txns =
                    getTransactionsUseCase.getTransactions(
                        GetTransactionsQuery(from, to, userId, type = TransactionType.EXPENSES, accountIban = iban),
                    )
                val lookup = buildGroupLookup(getCategoriesUseCase.getCategories(userId))
                txns to lookup
            }
        return transactions.toSankeyResponse(groupLookup)
    }

    // Builds a lookup from (category, subcategory) → group, derived from the categories table.
    private fun buildGroupLookup(categories: List<Category>): Map<Pair<String?, String?>, String> =
        categories
            .filter { it.group != null }
            .associate { (it.name to it.subcategory) to it.group!! }

    @GetMapping("/list")
    suspend fun listTransactions(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) subcategory: String? = null,
        @RequestParam(required = false) categoryGroup: String? = null,
        @RequestParam(required = false) iban: String? = null,
        @RequestParam(required = false) type: TransactionType = TransactionType.ALL,
        @RequestParam(required = false) uncategorized: Boolean = false,
        @RequestParam(required = false) excludeCollectionId: Long? = null,
        @RequestParam(required = false) excludeBudgetId: Long? = null,
        @AuthenticationPrincipal principal: UserDetails,
    ): TransactionListResponse {
        val transactions =
            withContext(Dispatchers.IO) {
                val userId = resolveUserUseCase.resolveUser(principal.username)
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(
                        from = from,
                        to = to,
                        userId = userId,
                        type = type,
                        accountIban = iban,
                        category = category,
                        subcategory = subcategory,
                        categoryGroup = categoryGroup,
                        uncategorized = uncategorized,
                        excludeCollectionId = excludeCollectionId,
                        excludeBudgetId = excludeBudgetId,
                    ),
                )
            }
        return TransactionListResponse(
            transactions =
                transactions
                    .sortedByDescending { it.accountingDate }
                    .map { it.toItem() },
            total = transactions.sumOf { it.effectiveAmount() },
        )
    }

    @PatchMapping("/{id}")
    suspend fun updateTransactionCategory(
        @PathVariable id: Long,
        @RequestBody request: UpdateCategoryRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<TransactionItem> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            val updated =
                updateTransactionCategoryUseCase.updateCategory(
                    id,
                    userId,
                    request.category,
                    request.subcategory,
                    request.categoryGroup,
                )
            if (updated != null) ResponseEntity.ok(updated.toItem()) else ResponseEntity.notFound().build()
        }

    @PatchMapping("/{id}/accounting-date")
    suspend fun updateTransactionAccountingDate(
        @PathVariable id: Long,
        @RequestBody request: UpdateAccountingDateRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<TransactionItem> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            val updated = updateTransactionAccountingDateUseCase.updateAccountingDate(id, userId, request.accountingDate)
            if (updated != null) ResponseEntity.ok(updated.toItem()) else ResponseEntity.notFound().build()
        }

    @PatchMapping("/{id}/comment")
    suspend fun updateTransactionComment(
        @PathVariable id: Long,
        @RequestBody request: UpdateCommentRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<TransactionItem> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            val updated = updateTransactionCommentUseCase.updateComment(id, userId, request.comment)
            if (updated != null) ResponseEntity.ok(updated.toItem()) else ResponseEntity.notFound().build()
        }

    @GetMapping("/trends")
    suspend fun getTrends(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) series: List<String>? = null,
        @RequestParam(required = false) granularity: Granularity = Granularity.MONTHLY,
        @RequestParam(required = false) iban: String? = null,
        @AuthenticationPrincipal principal: UserDetails,
    ): TrendsResponse {
        val effectiveSeries = series ?: emptyList()
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val buckets = generateBuckets(from, to, granularity)

        val groups =
            effectiveSeries.map { spec ->
                val parts = spec.split(":")
                val category = parts[0]
                val selectedGroup: String?
                val selectedSub: String?
                when (parts.size) {
                    3 -> {
                        selectedGroup = parts[1].ifEmpty { null }
                        selectedSub = parts[2].ifEmpty { null }
                    }
                    2 -> {
                        selectedGroup = null
                        selectedSub = parts[1].ifEmpty { null }
                    }
                    else -> {
                        selectedGroup = null
                        selectedSub = null
                    }
                }

                val allTransactions =
                    withContext(Dispatchers.IO) {
                        getTransactionsUseCase.getTransactions(
                            GetTransactionsQuery(
                                from = from,
                                to = to,
                                userId = userId,
                                type = TransactionType.EXPENSES,
                                accountIban = iban,
                                category = category,
                                categoryGroup = selectedGroup,
                            ),
                        )
                    }

                fun bucketSums(txns: List<Transaction>): List<BigDecimal> {
                    val byBucket = txns.groupBy { bucketKey(it.accountingDate, granularity) }
                    return buckets.map { bucket -> byBucket[bucket]?.sumOf { it.effectiveAmount().abs() } ?: BigDecimal.ZERO }
                }

                val mainLabel = if (selectedGroup != null) selectedGroup else category
                val hasSubSelection = selectedSub != null

                val mainEntry =
                    TrendSeriesEntry(
                        label = mainLabel,
                        data = bucketSums(allTransactions),
                        role = if (hasSubSelection) SeriesRole.MAIN_CONTEXT else SeriesRole.MAIN_SELECTED,
                    )

                val subEntries =
                    if (selectedGroup != null) {
                        allTransactions
                            .groupBy { it.subcategory }
                            .entries
                            .sortedBy { it.key }
                            .map { (subName, txns) ->
                                TrendSeriesEntry(
                                    label = subName,
                                    data = bucketSums(txns),
                                    role = if (subName == selectedSub) SeriesRole.SUB_SELECTED else SeriesRole.SUB_CONTEXT,
                                )
                            }
                    } else {
                        allTransactions
                            .groupBy { it.subcategory }
                            .entries
                            .sortedBy { it.key }
                            .map { (subName, txns) ->
                                TrendSeriesEntry(
                                    label = subName,
                                    data = bucketSums(txns),
                                    role = if (subName == selectedSub) SeriesRole.SUB_SELECTED else SeriesRole.SUB_CONTEXT,
                                )
                            }
                    }

                TrendSeriesGroup(main = mainEntry, subs = subEntries)
            }

        return TrendsResponse(granularity = granularity, buckets = buckets, groups = groups)
    }

    @GetMapping("/cashflow")
    suspend fun getCashflow(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) granularity: Granularity = Granularity.MONTHLY,
        @RequestParam(required = false) iban: String? = null,
        @AuthenticationPrincipal principal: UserDetails,
    ): CashflowResponse =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            getCashflowUseCase.getCashflow(
                GetCashflowQuery(
                    from = from,
                    to = to,
                    userId = userId,
                    granularity = granularity,
                    accountIban = iban,
                ),
            )
        }

    @GetMapping("/burnrate")
    suspend fun getBurnRate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) iban: String? = null,
        @RequestParam(required = false) rollingWindow: Int = 7,
        @AuthenticationPrincipal principal: UserDetails,
    ): BurnRateResponse =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            getBurnRateUseCase.getBurnRate(
                GetBurnRateQuery(
                    from = from,
                    to = to,
                    userId = userId,
                    accountIban = iban,
                    rollingWindow = rollingWindow,
                ),
            )
        }

    private fun List<Transaction>.toSankeyResponse(groupLookup: Map<Pair<String?, String?>, String> = emptyMap()): SankeyResponse {
        val nodeIndex = linkedMapOf<String, Int>()

        fun indexFor(key: String) = nodeIndex.getOrPut(key) { nodeIndex.size }

        data class TxKey(
            val category: String?,
            val group: String?,
            val subcategory: String?,
        )
        val aggregated =
            groupBy {
                val resolvedGroup =
                    it.categoryGroup
                        ?: groupLookup[it.category to it.subcategory]
                TxKey(it.category, resolvedGroup, it.subcategory)
            }.mapValues { (_, txns) -> txns.sumOf { it.effectiveAmount().abs() } }

        // Node registration order: categories (left), groups (middle), subcategories (right)
        aggregated.keys.forEach { k -> indexFor("cat:${k.category ?: ""}") }
        aggregated.keys.filter { it.group != null }.forEach { k -> indexFor("grp:${k.category ?: ""}:${k.group}") }
        aggregated.keys.forEach { k ->
            if (k.group != null) {
                indexFor("sub:${k.category ?: ""}:${k.group}:${k.subcategory ?: ""}")
            } else {
                indexFor("sub:${k.category ?: ""}::${k.subcategory ?: ""}")
            }
        }

        val catGrpAmounts = mutableMapOf<Pair<String?, String?>, BigDecimal>()
        val grpSubAmounts = mutableMapOf<Triple<String?, String?, String?>, BigDecimal>()
        val catSubAmounts = mutableMapOf<Pair<String?, String?>, BigDecimal>()

        aggregated.forEach { (key, amount) ->
            if (key.group != null) {
                catGrpAmounts.merge(key.category to key.group, amount, BigDecimal::add)
                grpSubAmounts.merge(Triple(key.category, key.group, key.subcategory), amount, BigDecimal::add)
            } else {
                catSubAmounts.merge(key.category to key.subcategory, amount, BigDecimal::add)
            }
        }

        val links = mutableListOf<SankeyLink>()
        catGrpAmounts.forEach { (catGrp, amt) ->
            val (cat, grp) = catGrp
            links.add(
                SankeyLink(
                    source = nodeIndex.getValue("cat:${cat ?: ""}"),
                    target = nodeIndex.getValue("grp:${cat ?: ""}:$grp"),
                    value = amt,
                ),
            )
        }
        grpSubAmounts.forEach { (triple, amt) ->
            val (cat, grp, sub) = triple
            links.add(
                SankeyLink(
                    source = nodeIndex.getValue("grp:${cat ?: ""}:$grp"),
                    target = nodeIndex.getValue("sub:${cat ?: ""}:$grp:${sub ?: ""}"),
                    value = amt,
                ),
            )
        }
        catSubAmounts.forEach { (catSub, amt) ->
            val (cat, sub) = catSub
            links.add(
                SankeyLink(
                    source = nodeIndex.getValue("cat:${cat ?: ""}"),
                    target = nodeIndex.getValue("sub:${cat ?: ""}::${sub ?: ""}"),
                    value = amt,
                ),
            )
        }

        val totals = mutableMapOf<Int, BigDecimal>()
        links.forEach { link ->
            totals.merge(link.source, link.value, BigDecimal::add)
            totals.merge(link.target, link.value, BigDecimal::add)
        }

        val nodes =
            nodeIndex.entries.sortedBy { it.value }.map { (key, idx) ->
                SankeyNode(
                    name = key.substringAfterLast(':'),
                    value = totals.getOrDefault(idx, BigDecimal.ZERO),
                    nodeKey = key,
                )
            }

        return SankeyResponse(nodes = nodes, links = links)
    }
}
