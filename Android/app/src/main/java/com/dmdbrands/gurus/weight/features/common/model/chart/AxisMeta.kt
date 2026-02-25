package com.dmdbrands.gurus.weight.features.common.model.chart

import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues

data class AxisMeta(
  val axisRange: CartesianRangeValues,
  val axisStep: Double? = null
)
