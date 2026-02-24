package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.component.shapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlin.math.roundToInt
import android.graphics.Typeface
import android.text.Layout

@Composable
fun rememberGoalMarker(
  goal: Goal? = null,
  isWeightlessOn: Boolean = false
): VerticalAxis.MarkerDecoration? {
  if (goal == null || (goal.goalType == null &&  goal.goalWeight == 0.0))  {
    return null
  }
  val resources = LocalResources.current
  val openSans: Typeface = resources.getFont(R.font.open_sans_semi_bold)

  val fill = fill(Color(0xFF458239))
  val labelComponent =
    rememberTextComponent(
      textAlignment = Layout.Alignment.ALIGN_CENTER,
      typeface = openSans,
      textSize = 14.sp,
      color = MeTheme.colorScheme.primaryBackground,
      padding = insets(horizontal = 10.dp, vertical = 2.dp),
      background =
        shapeComponent(
          fill,
          shape = CorneredShape.Pill,
        ),
    )

  return remember(goal, isWeightlessOn) {
    val goalValue = goal.goalWeight.div(10).roundToInt()
    // In weightless mode: negative shows as-is (e.g. -5), non-negative shows with + (e.g. +5)
    val labelText = if (isWeightlessOn) {
      if (goalValue < 0) goalValue.toString() else "+$goalValue"
    } else {
      goalValue.toString()
    }

    VerticalAxis.MarkerDecoration(
      y = { goalValue.toDouble() },
      markerComponent = labelComponent,
      label = { labelText },
      horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Outside,
      verticalLabelPosition = Position.Vertical.Center,
      outsideRangeOffset = 60f
    )
  }
}

@PreviewTheme
@Composable
fun GraphDecorationPreview() {
  MeAppTheme {
    rememberGoalMarker()
  }
}

