package com.dmdbrands.gurus.weight.features.common.components.chart

import android.text.Layout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.common.Position
import androidx.compose.foundation.shape.CircleShape
import kotlin.math.roundToInt

@Composable
fun rememberGoalMarker(
  goal: Goal? = null,
  isWeightlessOn: Boolean = false,
): VerticalAxis.MarkerDecoration? {
  if (goal == null || goal.goalWeight == 0.0)  {
    return null
  }
  val openSansFamily = FontFamily(Font(R.font.open_sans_semi_bold))

  val fill = Fill(Color(0xFF458239))
  val labelComponent =
    rememberTextComponent(
      style = TextStyle(
        fontFamily = openSansFamily,
        fontSize = 14.sp,
        color = MeTheme.colorScheme.primaryBackground,
      ),
      padding = Insets(horizontal = 10.dp, vertical = 2.dp),
      background =
        rememberShapeComponent(
          fill,
          shape = CircleShape,
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
      verticalLabelPosition = Position.Vertical.Center,
      outsideRangeOffset = 60f,
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
