package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields

@RestController
@RequestMapping("/transactions")
class TransactionQueryController(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @GetMapping("/sankey")
    suspend fun getSankeyData(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) iban: String? = null,
        @RequestHeader("X-User-Id") externalId: String,
    ): SankeyResponse {
        val transactions =
            withContext(Dispatchers.IO) {
                val userId = resolveUserUseCase.resolveUser(externalId)
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(from, to, userId, onlyNegative = true, accountIban = iban),
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
        @RequestParam(required = false) iban: String? = null,
        @RequestHeader("X-User-Id") externalId: String,
    ): TransactionListResponse {
        val transactions =
            withContext(Dispatchers.IO) {
                val userId = resolveUserUseCase.resolveUser(externalId)
                getTransactionsUseCase.getTransactions(
                    GetTransactionsQuery(
                        from = from,
                        to = to,
                        userId = userId,
                        onlyNegative = true,
                        accountIban = iban,
                        category = category,
                        subcategory = subcategory,
                    ),
                )
            }
        return TransactionListResponse(
            transactions =
                transactions
                    .sortedByDescending { it.bookingDate }
                    .map {
                        TransactionItem(
                            bookingDate = it.bookingDate.toString(),
                            category = it.category,
                            subcategory = it.subcategory,
                            amount = it.amount,
                            currency = it.currency,
                        )
                    },
            total = transactions.sumOf { it.amount },
        )
    }

    @GetMapping("/trends")
    suspend fun getTrends(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) series: List<String>? = null,
        @RequestParam(required = false) granularity: Granularity = Granularity.MONTHLY,
        @RequestParam(required = false) iban: String? = null,
        @RequestHeader("X-User-Id") externalId: String,
    ): TrendsResponse {
        val effectiveSeries = series ?: emptyList()
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(externalId) }
        val buckets = generateBuckets(from, to, granularity)

        val groups =
            effectiveSeries.map { spec ->
                val colonIdx = spec.indexOf(':')
                val category = if (colonIdx < 0) spec else spec.substring(0, colonIdx)
                val selectedSub = if (colonIdx < 0) null else spec.substring(colonIdx + 1)

                // One query for the whole category; subcategory breakdown comes from in-memory grouping.
                val allTransactions =
                    withContext(Dispatchers.IO) {
                        getTransactionsUseCase.getTransactions(
                            GetTransactionsQuery(from = from, to = to, userId = userId, accountIban = iban, category = category),
                        )
                    }

                fun bucketSums(txns: List<Transaction>): List<BigDecimal> {
                    val byBucket = txns.groupBy { bucketKey(it.bookingDate, granularity) }
                    return buckets.map { bucket -> byBucket[bucket]?.sumOf { it.amount.abs() } ?: BigDecimal.ZERO }
                }

                val mainEntry =
                    TrendSeriesEntry(
                        label = category,
                        data = bucketSums(allTransactions),
                        role = if (selectedSub != null) SeriesRole.MAIN_CONTEXT else SeriesRole.MAIN_SELECTED,
                    )

                val subEntries =
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

                TrendSeriesGroup(main = mainEntry, subs = subEntries)
            }

        return TrendsResponse(granularity = granularity, buckets = buckets, groups = groups)
    }

    private fun generateBuckets(
        from: LocalDate,
        to: LocalDate,
        granularity: Granularity,
    ): List<String> =
        when (granularity) {
            Granularity.MONTHLY ->
                generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
                    .takeWhile { !it.isAfter(YearMonth.from(to)) }
                    .map { it.toString() }
                    .toList()

            Granularity.WEEKLY ->
                generateSequence(from.with(DayOfWeek.MONDAY)) { it.plusWeeks(1) }
                    .takeWhile { !it.isAfter(to) }
                    .map { weekKey(it) }
                    .toList()

            Granularity.DAILY ->
                generateSequence(from) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(to) }
                    .map { it.toString() }
                    .toList()
        }

    private fun bucketKey(
        date: LocalDate,
        granularity: Granularity,
    ): String =
        when (granularity) {
            Granularity.MONTHLY -> YearMonth.from(date).toString()
            Granularity.WEEKLY -> weekKey(date.with(DayOfWeek.MONDAY))
            Granularity.DAILY -> date.toString()
        }

    private fun weekKey(monday: LocalDate): String =
        "${monday.get(IsoFields.WEEK_BASED_YEAR)}-W${String.format("%02d", monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))}"

    private fun List<Transaction>.toSankeyResponse(): SankeyResponse {
        // Keys are prefixed so that:
        //   - a name appearing as both a category and subcategory gets distinct indices
        //   - subcategories with the same name under different categories each get their
        //     own right-side node, keeping links for different categories from crossing
        val nodeIndex = linkedMapOf<String, Int>()

        fun indexFor(key: String) = nodeIndex.getOrPut(key) { nodeIndex.size }

        val aggregated =
            groupBy { it.category to it.subcategory }
                .mapValues { (_, txns) -> txns.sumOf { it.amount.abs() } }

        // Register categories before subcategories so they appear on the left.
        aggregated.keys.forEach { (category, _) -> indexFor("cat:$category") }
        aggregated.keys.forEach { (category, subcategory) -> indexFor("sub:$category:$subcategory") }

        val links =
            aggregated.map { (key, amount) ->
                val (category, subcategory) = key
                SankeyLink(
                    source = nodeIndex.getValue("cat:$category"),
                    target = nodeIndex.getValue("sub:$category:$subcategory"),
                    value = amount,
                )
            }

        val totals = mutableMapOf<Int, BigDecimal>()
        links.forEach { link ->
            totals.merge(link.source, link.value, BigDecimal::add)
            totals.merge(link.target, link.value, BigDecimal::add)
        }

        val nodes =
            nodeIndex.entries
                .sortedBy { it.value }
                .map { (key, idx) ->
                    SankeyNode(
                        name = key.substringAfterLast(':'),
                        value = totals.getOrDefault(idx, BigDecimal.ZERO),
                        nodeKey = key,
                    )
                }

        return SankeyResponse(nodes = nodes, links = links)
    }
}
