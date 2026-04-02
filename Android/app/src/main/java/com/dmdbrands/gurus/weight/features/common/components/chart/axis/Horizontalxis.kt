package com.dmdbrands.gurus.weight.features.common.components.chart.axis

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.common.Fill

@Composable
internal fun topAxis() = HorizontalAxis.rememberTop(
  label = null,
  line = rememberAxisLineComponent(
    fill = Fill(MeTheme.colorScheme.iconSecondaryDisabled),
    thickness = 1.dp,
  ),
  guideline = null,
  tickLength = 0.dp,
)

@Composable
internal fun bottomAxis(
  segment: GraphSegment,
  separators: List<Double> = emptyList(),
  horizontalItemPlacer: HorizontalAxis.ItemPlacer = HorizontalAxis.ItemPlacer.aligned()
): Axis<Axis.Position.Horizontal.Bottom> {
  val openSansFamily = FontFamily(Font(R.font.open_sans_semi_bold))

  return if (segment != GraphSegment.TOTAL)
    HorizontalAxis.rememberBottom(
      valueFormatter =
        CartesianValueFormatter { _, value, _ ->
          if (value.toInt() != 0) GraphUtil.formatTimestampForSegment(
            value.toLong(),
            segment,
          ).lowercase() else "–"
        },
      itemPlacer = horizontalItemPlacer,
      guideline = rememberAxisGuidelineComponent(
        fill = Fill(MeTheme.colorScheme.utility),
        thickness = 1.dp,
      ),
      label = rememberAxisLabelComponent(
        style = TextStyle(
          fontFamily = openSansFamily,
          color = MeTheme.colorScheme.textSubheading,
          fontSize = 14.sp,
        ),
      ),
      tick = rememberAxisGuidelineComponent(),
      tickLength = 20.dp,
      line = rememberAxisLineComponent(
        fill = Fill(MeTheme.colorScheme.iconSecondaryDisabled),
        thickness = 1.dp,
      ),
      separators = HorizontalAxis.Separators(
        values = separators,
        line = rememberAxisLineComponent(
          fill = Fill(MeTheme.colorScheme.utility),
          thickness = 1.dp,
        ),
      ),
    )
  else
    HorizontalAxis.rememberBottom(
      guideline = null,
      itemPlacer = horizontalItemPlacer,
      label = null,
      tickLength = 20.dp,
      tick = rememberAxisGuidelineComponent(
        fill = Fill(Color.Transparent),
      ),
      line = rememberAxisLineComponent(
        fill = Fill(MeTheme.colorScheme.iconSecondaryDisabled),
        thickness = 1.dp,
      ),
    )
}
