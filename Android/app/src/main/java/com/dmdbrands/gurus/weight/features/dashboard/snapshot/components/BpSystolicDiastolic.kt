package com.dmdbrands.gurus.weight.features.dashboard.snapshot.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Figma's systolic/diastolic separator (node "Vector 98"): a thin 11×47dp diagonal line.
 * Replaces the heavy ExtraBold "/" text character that inherited the big-value font weight.
 */
@Composable
fun BpValueSeparator(
  modifier: Modifier = Modifier,
  width: Dp = 11.dp,
  height: Dp = 47.dp,
  strokeWidth: Dp = 2.dp,
  color: Color = MeTheme.colorScheme.textSubheading,
) {
  Canvas(modifier = modifier.size(width = width, height = height)) {
    drawLine(
      color = color,
      start = Offset(size.width, 0f),
      end = Offset(0f, size.height),
      strokeWidth = strokeWidth.toPx(),
      cap = StrokeCap.Round,
    )
  }
}

/**
 * Renders "sys / dia" with systolic and diastolic values colored by their severity range,
 * and a thin diagonal line separator (matches Figma's `imgVector98`).
 *
 * Callers should guard nulls and show a placeholder instead when either value is null.
 */
@Composable
fun BpSystolicDiastolic(
  systolic: Int,
  diastolic: Int,
  style: TextStyle,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text(text = "$systolic", style = style, color = SnapshotColors.systolicColor(systolic))
    BpValueSeparator()
    Text(text = "$diastolic", style = style, color = SnapshotColors.diastolicColor(diastolic))
  }
}
