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
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
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
  weightUnit: WeightUnit = WeightUnit.DISPLAY_DEFAULT,
  weightlessOffset: Double = 0.0,
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

  return remember(goal, isWeightlessOn, weightUnit, weightlessOffset) {
    // goal.goalWeight is already display lb (÷10 done in ViewModel)
    val goalLb = goal.goalWeight

    // Y position: in chart coordinate space (display lb)
    val yPosition = goalLb

    // Label text: apply weightless + unit conversion for display
    val adjusted = goalLb - weightlessOffset
    val converted = if (weightUnit == WeightUnit.KG)
      convertWeight(adjusted, WeightUnit.LB, WeightUnit.KG)
    else adjusted
    val displayValue = converted.roundToInt()

    val labelText = if (isWeightlessOn) {
      if (displayValue < 0) displayValue.toString() else "+$displayValue"
    } else {
      displayValue.toString()
    }

    VerticalAxis.MarkerDecoration(
      y = { yPosition },
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
