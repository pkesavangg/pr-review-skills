package com.dmdbrands.gurus.weight.features.common.model.chart

data class GraphLine(
  val name: String,             // e.g., "Heart Rate", "Calories", etc.
  val points: List<GraphPoint>  // Each point has x (Label) and y (Label)
)


