package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.domain.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/transactions")
class TransactionQueryController(
    private val getTransactionsUseCase: GetTransactionsUseCase,
) {
    @GetMapping("/sankey")
    suspend fun getSankeyData(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) iban: String? = null,
    ): SankeyResponse {
        val transactions =
            withContext(Dispatchers.IO) {
                getTransactionsUseCase.getTransactions(GetTransactionsQuery(from, to, onlyNegative = true, accountIban = iban))
            }
        return transactions.toSankeyResponse()
    }

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
                    )
                }

        return SankeyResponse(nodes = nodes, links = links)
    }
}
