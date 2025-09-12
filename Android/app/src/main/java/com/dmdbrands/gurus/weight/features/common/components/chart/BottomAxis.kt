package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.common.Position

@Composable
internal fun bottomAxis(
  segment: GraphSegment,
  separators: List<Double> = emptyList(),
  horizontalItemPlacer: HorizontalAxis.ItemPlacer = HorizontalAxis.ItemPlacer.aligned()
): Axis<Axis.Position.Horizontal.Bottom> {
  return if (segment != GraphSegment.TOTAL)
    HorizontalAxis.rememberBottom(
      valueFormatter =
        CartesianValueFormatter { _, value, _ ->
          if (value.toInt() != 0) GraphUtil.formatTimestampForSegment(
            value.toLong(),
            segment,
          ).lowercase() else " "
        },
      itemPlacer = horizontalItemPlacer,
      guideline = rememberAxisGuidelineComponent(
        fill = fill(MeTheme.colorScheme.utility.copy(0.5f)),
        thickness = 1.dp,
      ),
      label = rememberAxisLabelComponent(
        color = MeTheme.colorScheme.textSubheading,
        textSize = 14.sp,
      ),
      tick = rememberAxisGuidelineComponent(),
      tickLength = 20.dp,
      horizontalLabelPosition = Position.Horizontal.End,
      line = rememberAxisLineComponent(
        fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
        thickness = 1.dp,
      ),
      separators = { separators },
    )
  else
    HorizontalAxis.rememberBottom(
      guideline = null,
      itemPlacer = horizontalItemPlacer,
      valueFormatter =
        CartesianValueFormatter { _, value, _ ->
          " "
        },
      horizontalLabelPosition = Position.Horizontal.End,
      tick = rememberAxisGuidelineComponent(fill = fill(Color.Transparent)),
      tickLength = 20.dp,
      line = rememberAxisLineComponent(
        fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
        thickness = 1.dp,
      ),
    )
}
