package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.InterpolationType
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import androidx.compose.foundation.shape.CircleShape

@Composable
internal fun rememberDefaultMarker(
  state: GraphState,
  segment: GraphSegment,
  onTargetsUpdate: (List<PeriodBodyScaleSummary>) -> Unit
): CartesianMarker {
  fun yLabelCallback(): (List<List<Double>>) -> Unit = { fallbackValues ->
    val data = state.data.filter {
      DateTimeConverter.isoToTimestamp(it.entryTimestamp).toDouble() == state.markerIndex?.toDouble()
    }
    val requiredData = data.ifEmpty {
      state.createFallBackData(segment = segment, fallbackValues = fallbackValues)
    }
    onTargetsUpdate(requiredData)
  }

  val openSansFamily = FontFamily(Font(R.font.open_sans_regular))

  val label =
    rememberTextComponent(
      style = TextStyle(
        fontFamily = openSansFamily,
        color = MeTheme.colorScheme.textSubheading,
        fontSize = 14.sp,
      ),
    )
  val guideline = rememberAxisLineComponent(
    fill = Fill(MeTheme.colorScheme.textBody),
    thickness = 1.dp,
  )
  val pointSize = if (segment == GraphSegment.TOTAL) 10f else 16f


  return rememberDefaultCartesianMarker(
    label = label,
    labelPosition = DefaultCartesianMarker.LabelPosition.Top,
    valueFormatter = valueFormatter(segment),
    indicator = { color ->
      ShapeComponent(
        fill = Fill(color),
        shape = CircleShape,
        strokeFill = Fill(color),
        strokeThickness = 0.dp,
      )
    },
    indicatorSize = pointSize.dp,
    guideline = guideline,
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
