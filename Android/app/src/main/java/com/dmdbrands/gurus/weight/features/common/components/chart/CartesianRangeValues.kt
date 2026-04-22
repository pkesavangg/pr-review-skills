package com.dmdbrands.gurus.weight.features.common.components.chart

/**
 * Local replacement for [com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues]
 * which was removed in vico v4. Holds optional min/max X/Y range values for chart axes.
 */
data class CartesianRangeValues(
  val minX: Double? = null,
  val maxX: Double? = null,
  val minY: Double? = null,
  val maxY: Double? = null,
)
