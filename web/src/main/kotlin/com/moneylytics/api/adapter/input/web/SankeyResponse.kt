package com.moneylytics.api.adapter.input.web

import java.math.BigDecimal

enum class Granularity { MONTHLY, WEEKLY, DAILY }

data class TrendsResponse(
    val granularity: Granularity,
    val buckets: List<String>,
    val series: List<TrendSeries>,
)

data class TrendSeries(
    val label: String,
    val data: List<BigDecimal>,
)

data class SankeyResponse(
    val nodes: List<SankeyNode>,
    val links: List<SankeyLink>,
)

data class SankeyNode(
    val name: String,
    val value: BigDecimal,
    val nodeKey: String,
)

data class SankeyLink(
    val source: Int,
    val target: Int,
    val value: BigDecimal,
)
