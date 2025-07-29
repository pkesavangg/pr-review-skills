package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.enums.GraphSegment
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil
import com.greatergoods.meapp.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter

@Composable
internal fun bottomAxis(
  segment: GraphSegment,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer
): Axis<Axis.Position.Horizontal.Bottom> {
  return if (segment != GraphSegment.TOTAL)
    HorizontalAxis.rememberBottom(
      valueFormatter =
        CartesianValueFormatter { _, value, _ ->
          if (value.toInt() != 0) GraphUtil.formatTimestampForSegment(
            value.toLong(),
            segment,
          ) else " "
        },
      itemPlacer = horizontalItemPlacer,
      guideline = rememberAxisGuidelineComponent(
        fill = fill(MeTheme.colorScheme.utility.copy(0.5f)),
        thickness = 1.dp,
      ),
      label = rememberTextComponent(color = MeTheme.colorScheme.textSubheading),
      tickLength = 0.dp,
      line = rememberAxisLineComponent(
        fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
        thickness = 1.dp,
      ),
    )
  else
    HorizontalAxis.rememberBottom(
      guideline = null,
      itemPlacer = horizontalItemPlacer,
      label = null,
      tickLength = 0.dp,
      line = rememberAxisLineComponent(
        fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
        thickness = 1.dp,
      ),
    )
}
