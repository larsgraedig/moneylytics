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
        @RequestParam(required = false) iban: String? = null,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): SankeyResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val transactions =
            withContext(Dispatchers.IO) {
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, organizationId, type = TransactionType.EXPENSES, accountIban = iban),
                )
            }
        return transactions.toSankeyResponse()
    }

    @GetMapping("/list")
    suspend fun listTransactions(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) subcategory: String? = null,
        @RequestParam(required = false) group: String? = null,
        @RequestParam(required = false) iban: String? = null,
        @RequestParam(required = false) type: TransactionType = TransactionType.ALL,
        @RequestParam(required = false) uncategorized: Boolean = false,
        @RequestParam(required = false) excludeCollectionId: Long? = null,
        @RequestParam(required = false) excludeBudgetId: Long? = null,
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
                        accountIban = iban,
                        category = category,
                        subcategory = subcategory,
                        group = group,
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
        @RequestParam(required = false) iban: String? = null,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): TrendsResponse {
        val effectiveSeries = series ?: emptyList()
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val buckets = generateBuckets(from, to, granularity)

        val groups =
            effectiveSeries.map { spec ->
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
                                accountIban = iban,
                                category = category,
                                subcategory = selectedSub,
                            ),
                        )
                    }

                fun bucketSums(txns: List<Transaction>): List<BigDecimal> {
                    val byBucket = txns.groupBy { bucketKey(it.accountingDate, granularity) }
                    return buckets.map { bucket -> byBucket[bucket]?.sumOf { it.effectiveAmount().abs() } ?: BigDecimal.ZERO }
                }

                val mainLabel = if (selectedSub != null) selectedSub else category
                val hasGroupSelection = selectedGroup != null

                val mainEntry =
                    TrendSeriesEntry(
                        label = mainLabel,
                        data = bucketSums(allTransactions),
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
                                data = bucketSums(txns),
                                role = if (groupName == selectedGroup) SeriesRole.SUB_SELECTED else SeriesRole.SUB_CONTEXT,
                            )
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
                    accountIban = iban,
                ),
            )
        }
    }

    @GetMapping("/category-totals")
    suspend fun getCategoryTotals(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) iban: String? = null,
        @RequestParam(required = false) category: String? = null,
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
                    accountIban = iban,
                    category = category,
                ),
            )
        }
    }

    @GetMapping("/burnrate")
    suspend fun getBurnRate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) iban: String? = null,
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
                    accountIban = iban,
                    rollingWindow = rollingWindow,
                ),
            )
        }
    }

    private fun List<Transaction>.toSankeyResponse(): SankeyResponse {
        val nodeIndex = linkedMapOf<String, Int>()

        fun indexFor(key: String) = nodeIndex.getOrPut(key) { nodeIndex.size }

        data class TxKey(
            val category: String?,
            val subcategory: String?,
            val group: String?,
        )
        val aggregated =
            groupBy { TxKey(it.category, it.subcategory, it.group) }
                .mapValues { (_, txns) -> txns.sumOf { it.effectiveAmount().abs() } }

        aggregated.keys.forEach { k -> indexFor("cat:${k.category ?: ""}") }
        aggregated.keys.filter { it.subcategory != null }.forEach { k -> indexFor("sub:${k.category ?: ""}:${k.subcategory}") }
        aggregated.keys.forEach { k ->
            if (k.subcategory != null) {
                indexFor("grp:${k.category ?: ""}:${k.subcategory}:${k.group ?: ""}")
            } else {
                indexFor("grp:${k.category ?: ""}::${k.group ?: ""}")
            }
        }

        val catSubAmounts = mutableMapOf<Pair<String?, String?>, BigDecimal>()
        val subGrpAmounts = mutableMapOf<Triple<String?, String?, String?>, BigDecimal>()
        val catGrpAmounts = mutableMapOf<Pair<String?, String?>, BigDecimal>()

        aggregated.forEach { (key, amount) ->
            if (key.subcategory != null) {
                catSubAmounts.merge(key.category to key.subcategory, amount, BigDecimal::add)
                subGrpAmounts.merge(Triple(key.category, key.subcategory, key.group), amount, BigDecimal::add)
            } else {
                catGrpAmounts.merge(key.category to key.group, amount, BigDecimal::add)
            }
        }

        val links = mutableListOf<SankeyLink>()
        catSubAmounts.forEach { (catSub, amt) ->
            val (cat, sub) = catSub
            links.add(
                SankeyLink(
                    source = nodeIndex.getValue("cat:${cat ?: ""}"),
                    target = nodeIndex.getValue("sub:${cat ?: ""}:$sub"),
                    value = amt,
                ),
            )
        }
        subGrpAmounts.forEach { (triple, amt) ->
            val (cat, sub, grp) = triple
            links.add(
                SankeyLink(
                    source = nodeIndex.getValue("sub:${cat ?: ""}:$sub"),
                    target = nodeIndex.getValue("grp:${cat ?: ""}:$sub:${grp ?: ""}"),
                    value = amt,
                ),
            )
        }
        catGrpAmounts.forEach { (catGrp, amt) ->
            val (cat, grp) = catGrp
            links.add(
                SankeyLink(
                    source = nodeIndex.getValue("cat:${cat ?: ""}"),
                    target = nodeIndex.getValue("grp:${cat ?: ""}::${grp ?: ""}"),
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
