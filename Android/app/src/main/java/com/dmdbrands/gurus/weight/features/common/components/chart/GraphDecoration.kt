package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.component.shapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import android.graphics.Typeface
import android.text.Layout

@Composable
fun rememberHorizontalLine(goal: com.dmdbrands.gurus.weight.domain.model.goal.Goal? = null): Decoration? {
  if (goal == null || goal.goalWeight == 0.0) return null
  val fill = fill(Color(0xFF458239))
  val line = rememberLineComponent(fill = fill(Color(0xFF458239)), thickness = 2.dp)
  val labelComponent =
    rememberTextComponent(
      minWidth = TextComponent.MinWidth.fixed(40f),
      textAlignment = Layout.Alignment.ALIGN_CENTER,
      typeface = Typeface.DEFAULT_BOLD,
      textSize = 14.sp,
      color = MeTheme.colorScheme.primaryBackground,
      margins = insets(end = (-38).dp),
      padding = insets(horizontal = 8.dp, vertical = 2.dp),
      background =
        shapeComponent(
          fill,
          shape = CorneredShape.Pill,
        ),
    )

  val decoration =
    object : Decoration {
      override fun drawOverLayers(context: CartesianDrawingContext) {
        HorizontalLine(
          y = { goal.goalWeight.div(10) },
          label = {goal.goalWeight.div(10).toInt().toString()},
          line = line.copy(fill = fill(Color.Transparent)),
          labelComponent = labelComponent,
          horizontalLabelPosition = Position.Horizontal.End,
          verticalLabelPosition = Position.Vertical.Center,
          verticalAxisPosition = Axis.Position.Vertical.End,
        ).drawOverLayers(context)
      }
    }
  return remember { decoration }
}

@PreviewTheme
@Composable
fun GraphDecorationPreview() {
  MeAppTheme {
    rememberHorizontalLine()
  }
}

