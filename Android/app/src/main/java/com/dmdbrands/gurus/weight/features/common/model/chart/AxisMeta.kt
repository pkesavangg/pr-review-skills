package com.dmdbrands.gurus.weight.features.common.model.chart

import com.dmdbrands.gurus.weight.features.common.components.chart.CartesianRangeValues

data class AxisMeta(
  val axisRange: CartesianRangeValues,
  val axisStep: Double? = null
)
