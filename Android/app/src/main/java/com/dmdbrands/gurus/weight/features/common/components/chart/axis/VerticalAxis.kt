package com.dmdbrands.gurus.weight.features.common.components.chart.axis

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.axis.scroll
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import kotlin.math.roundToInt
import android.graphics.Typeface

@Composable
fun startAxis() = VerticalAxis.rememberStart(
  label = null,
  size = BaseAxis.Size.scroll(8.dp, isLabelsScrollable = true),
  line = rememberAxisLineComponent(
    fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
    thickness = 1.dp,
  ),
  guideline = null,
  tickLength = 0.dp,
)

@Composable
fun endAxis(
  yStep: Double? = null,
  isEmptyGraph: Boolean,
  markerDecoration: VerticalAxis.MarkerDecoration? = null
): VerticalAxis<Axis.Position.Vertical.End> {
  val resources = LocalResources.current
  val openSans: Typeface = resources.getFont(R.font.open_sans_semi_bold)

  val animatedStep = if (yStep != null) animateIntAsState(
    targetValue = yStep.roundToInt(),
    animationSpec = tween(durationMillis = 250, delayMillis = 0, easing = EaseInOut),
    label = "animatedStep",
  ) else null

  return VerticalAxis.rememberEnd(
    valueFormatter = CartesianValueFormatter { _, value, _ ->
      if (isEmptyGraph && markerDecoration == null) " " else
        value.roundToInt().toString()
    },
    itemPlacer = remember(animatedStep?.value) {
      VerticalAxis.ItemPlacer.step(
        { animatedStep?.value?.toDouble() },
      )
    },
    size = BaseAxis.Size.scroll(50.dp),
    line =
      rememberAxisLineComponent(
        fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
        thickness = 1.dp,
      ),
    markerDecoration = markerDecoration,
    guideline = if (isEmptyGraph && markerDecoration == null) null else
      rememberAxisLineComponent(
        fill = fill(MeTheme.colorScheme.utility.copy(0.5f)),
        thickness = 1.dp,
      ),
    label =
      rememberTextComponent(
        typeface = openSans,
        color = MeTheme.colorScheme.textSubheading,
        textSize = 14.sp,
      ),
    tickLength = 0.dp,
  )
}
