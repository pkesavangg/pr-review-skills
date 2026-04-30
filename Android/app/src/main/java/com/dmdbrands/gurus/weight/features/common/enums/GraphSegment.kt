package com.dmdbrands.gurus.weight.features.common.enums

import com.dmdbrands.gurus.weight.proto.DefaultGraphSegment

enum class GraphSegment {
    WEEK, MONTH, YEAR, TOTAL;

    companion object {
        /** Single source of truth for the fallback / fresh-install default. */
        val DEFAULT: GraphSegment = MONTH
    }
}

fun DefaultGraphSegment.toGraphSegment(): GraphSegment = when (this) {
    DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_WEEK -> GraphSegment.WEEK
    DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH -> GraphSegment.MONTH
    DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_YEAR -> GraphSegment.YEAR
    DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_TOTAL -> GraphSegment.TOTAL
    else -> GraphSegment.DEFAULT // UNSPECIFIED / UNRECOGNIZED → fallback
}

fun GraphSegment.toDefaultGraphSegment(): DefaultGraphSegment = when (this) {
    GraphSegment.WEEK -> DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_WEEK
    GraphSegment.MONTH -> DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH
    GraphSegment.YEAR -> DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_YEAR
    GraphSegment.TOTAL -> DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_TOTAL
}
