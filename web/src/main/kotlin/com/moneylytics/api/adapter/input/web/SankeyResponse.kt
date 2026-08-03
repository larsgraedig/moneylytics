package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.Granularity
import java.math.BigDecimal

enum class SeriesRole {
    /** Category-only config: the thick aggregate line. */
    MAIN_SELECTED,

    /** Subcategory config: thin dashed aggregate; makes context clear without dominating. */
    MAIN_CONTEXT,

    /** Subcategory config: the thick line for the explicitly chosen subcategory. */
    SUB_SELECTED,

    /** Thin line for every subcategory that is not the primary focus. */
    SUB_CONTEXT,
}

data class TrendsResponse(
    val granularity: Granularity,
    val buckets: List<String>,
    val groups: List<TrendSeriesGroup>,
)

data class TrendSeriesGroup(
    val main: TrendSeriesEntry,
    val subs: List<TrendSeriesEntry>,
)

data class TrendSeriesEntry(
    val label: String?,
    val data: List<BigDecimal>,
    val role: SeriesRole,
    val categoryId: Long? = null,
)

data class SankeyResponse(
    val nodes: List<SankeyNode>,
    val links: List<SankeyLink>,
)

data class SankeyNode(
    val name: String,
    val value: BigDecimal,
    val nodeKey: String,
    val categoryId: Long,
    val namePath: List<String>,
)

data class SankeyLink(
    val source: Int,
    val target: Int,
    val value: BigDecimal,
)
