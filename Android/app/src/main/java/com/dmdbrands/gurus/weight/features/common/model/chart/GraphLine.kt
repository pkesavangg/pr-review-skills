package com.dmdbrands.gurus.weight.features.common.model.chart

import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker

data class GraphLine(
    val name: String,             // e.g., "Heart Rate", "Calories", etc.
    val points: List<GraphPoint>  // Each point has x (Label) and y (Label)
)


