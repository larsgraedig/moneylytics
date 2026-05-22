package com.moneylytics.api.adapter.input.web

import java.math.BigDecimal

data class SankeyResponse(
    val nodes: List<SankeyNode>,
    val links: List<SankeyLink>,
)

data class SankeyNode(
    val name: String,
    val value: BigDecimal,
)

data class SankeyLink(
    val source: Int,
    val target: Int,
    val value: BigDecimal,
)
