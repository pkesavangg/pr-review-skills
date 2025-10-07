package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.InterpolationType
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import android.graphics.Typeface

@Composable
internal fun rememberDefaultMarker(
  segment: GraphSegment,
  yLabelCallback: (List<List<Double>>) -> Unit = {}
): CartesianMarker {
  val resources = LocalResources.current
  val openSans: Typeface = resources.getFont(R.font.open_sans_regular)

  val label =
    rememberTextComponent(
      typeface = openSans,
      color = MeTheme.colorScheme.textSubheading,
      textSize = 14.sp,
    )
  val guideline = rememberAxisLineComponent(
    fill = fill(MeTheme.colorScheme.textBody),
    thickness = 1.dp,
  )
  val pointSize = if (segment == GraphSegment.TOTAL) 14f else 16f


  return rememberDefaultCartesianMarker(
    label = label,
    labelPosition = DefaultCartesianMarker.LabelPosition.Top,
    valueFormatter = valueFormatter(segment),
    indicator = { color ->
      ShapeComponent(
        fill = fill(color),
        strokeFill = fill(color),
        shape = CorneredShape.Pill,
        strokeThicknessDp = 0f,
      )
    },
    indicatorSize = pointSize.dp,
    contentPadding = insets(vertical = 29.dp),
    guideline = guideline,
    yLabelCallback = yLabelCallback,
    interpolationType = InterpolationType.CUBIC,
    curvature = 0.5f,
  )
}

/**
 * Internal helper to remember the value formatter for the marker.
 */
@Composable
private fun valueFormatter(
  segment: GraphSegment
): DefaultCartesianMarker.ValueFormatter =
  object : DefaultCartesianMarker.ValueFormatter {
    override fun format(
      context: CartesianDrawingContext,
      targets: List<CartesianMarker.Target>,
    ) = GraphUtil.markerValueFormatter(
      targets.first().x.toLong(),
      segment,
    ).lowercase()
  }

