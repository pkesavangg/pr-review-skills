package com.greatergoods.meapp.features.common.model.chart

import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker

data class GraphLine(
    val name: String,             // e.g., "Heart Rate", "Calories", etc.
    val points: List<GraphPoint>  // Each point has x (Label) and y (Label)
)


