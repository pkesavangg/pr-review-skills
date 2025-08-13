package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

@Composable
internal fun rememberDefaultMarker(
  xLabels: List<Label>,
  markerIndex: Int?,
  segment: GraphSegment
): CartesianMarker {
  val label =
    rememberTextComponent(
      color = MeTheme.colorScheme.textSubheading,
      textSize = 14.sp,
      padding = insets(top = -6.dp),
    )
  val guideline = rememberAxisLineComponent(
    fill = fill(MeTheme.colorScheme.textBody),
    thickness = 1.dp,
  )

  return rememberDefaultCartesianMarker(
    label = label,
    labelPosition = DefaultCartesianMarker.LabelPosition.Top,
    valueFormatter = valueFormatter(xLabels, markerIndex, segment),
    indicator = { color ->
      ShapeComponent(
        fill = fill(color),
        strokeFill = fill(color),
        shape = CorneredShape.Pill,
        strokeThicknessDp = 0f,
      )
    },
    guideline = guideline,
    contentPadding = insets(vertical = 22.dp),
  )
}

/**
 * Internal helper to remember the value formatter for the marker.
 */
@Composable
private fun valueFormatter(
  xLabels: List<Label>,
  markerIndex: Int?,
  segment: GraphSegment
): DefaultCartesianMarker.ValueFormatter =
  remember(xLabels, markerIndex) {
    object : DefaultCartesianMarker.ValueFormatter {
      override fun format(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>,
      ) = GraphUtil.markerValueFormatter(
        targets.first().x.toLong(),
        segment,
      )
    }
  }

