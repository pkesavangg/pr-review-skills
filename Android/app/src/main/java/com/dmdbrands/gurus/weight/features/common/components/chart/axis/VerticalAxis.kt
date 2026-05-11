package com.dmdbrands.gurus.weight.features.common.components.chart.axis

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.ListItemPlacer
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import kotlin.math.roundToInt

@Composable
fun startAxis(segment: GraphSegment, isSingleWindow: Boolean) = VerticalAxis.rememberStart(
  label = null,
  size = if (segment == GraphSegment.TOTAL || isSingleWindow) BaseAxis.Size.Fixed(8.dp) else BaseAxis.Size.Scroll(
    8.dp,
    isLabelsScrollable = true,
  ),
  line = rememberAxisLineComponent(
    fill = Fill(MeTheme.colorScheme.iconSecondaryDisabled),
    thickness = 1.dp,
  ),
  guideline = null,
  tick = null,
  tickLength = 0.dp,
)

@Composable
fun endAxis(
  isEmptyGraph: Boolean,
  markerDecoration: VerticalAxis.MarkerDecoration? = null,
  ticksProvider: (() -> List<Double>)? = null,
): VerticalAxis<Axis.Position.Vertical.End> {
  val openSansFamily = FontFamily(Font(R.font.open_sans_semi_bold))

  return VerticalAxis.rememberEnd(
    valueFormatter = CartesianValueFormatter { _, value, _ ->
      if (isEmptyGraph && markerDecoration == null) "–" else
        value.roundToInt().toString()
    },
    itemPlacer = if (ticksProvider != null) ListItemPlacer(ticksProvider) else VerticalAxis.ItemPlacer.step({ 1.0 }),
    size = BaseAxis.Size.Scroll(50.dp),
    line =
      rememberAxisLineComponent(
        fill = Fill(MeTheme.colorScheme.iconSecondaryDisabled),
        thickness = 1.dp,
      ),
    markerDecoration = markerDecoration,
    guideline = if (isEmptyGraph && markerDecoration == null) null else
      rememberAxisLineComponent(
        fill = Fill(MeTheme.colorScheme.utility.copy(0.5f)),
        thickness = 1.dp,
      ),
    label =
      rememberTextComponent(
        style = TextStyle(
          fontFamily = openSansFamily,
          color = MeTheme.colorScheme.textSubheading,
          fontSize = 14.sp,
        ),
      ),
    tickLength = 0.dp,
  )
}
