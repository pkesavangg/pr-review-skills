package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlin.math.roundToInt

/** Sunday-first week day labels for the empty-state X axis. */
private val WeekDayLabels = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

/** Width reserved for the right-hand Y-axis label gutter when a range is shown. */
private val YGutterWidth = 40.dp

/**
 * Y-axis range for [EmptyDashboardGraph]. When supplied, the grid renders right-aligned
 * axis labels (yMax at top → yMin at bottom) and, if [goalValue] is in range, a goal badge.
 */
data class EmptyGraphRange(
  val yMin: Double,
  val yMax: Double,
  val yStep: Double,
  val goalValue: Double? = null,
)

/** Per-product default Y ranges for the detail-dashboard empty grid (MOB-432 mocks). */
object EmptyGraphDefaults {
  /** Blood pressure: 50–140 mmHg. */
  val Bp = EmptyGraphRange(50.0, 140.0, 30.0)

  /** Baby weight: 10–25 lbs. */
  val BabyWeight = EmptyGraphRange(10.0, 25.0, 5.0)

  /** Baby height: 15–35 in. */
  val BabyHeight = EmptyGraphRange(15.0, 35.0, 5.0)

  /**
   * Weight range anchored to the user's goal so the goal badge + axis show. Returns null
   * when no goal is set (→ bare grid, matching the no-goal snapshot mock).
   */
  fun weightGoal(goalDisplay: Double?, isKg: Boolean): EmptyGraphRange? {
    if (goalDisplay == null || goalDisplay <= 0.0) return null
    val step = if (isKg) 2.0 else 5.0
    val min = kotlin.math.floor(goalDisplay / step) * step
    return EmptyGraphRange(yMin = min, yMax = min + 3 * step, yStep = step, goalValue = goalDisplay)
  }
}

/**
 * Static, data-less chart grid for the dashboard/snapshot "no entries" first-run state
 * (MOB-432). Draws the week-day X axis and gridlines; when a Y range is supplied it also
 * renders right-aligned Y-axis labels and an optional goal badge. No data line is drawn.
 *
 * Pass [yMin]/[yMax]/[yStep] to show the Y axis (e.g. BP 50–140, Baby 10–25, Weight goal
 * range). Omit them for a bare grid (snapshot cards / Weight with no goal).
 */
@Composable
fun EmptyDashboardGraph(
  modifier: Modifier = Modifier,
  height: Dp = 200.dp,
  range: EmptyGraphRange? = null,
) {
  val showYAxis = range != null && range.yStep > 0.0 && range.yMax > range.yMin
  val yTicks = if (showYAxis && range != null) range.toYTicks() else emptyList()
  val goalFraction = range?.takeIf { showYAxis }?.goalFractionFromTop()

  Column(modifier = modifier.height(height)) {
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      EmptyGraphPlot(
        yTickFractions = yTicks.map { it.second },
        goalValue = range?.goalValue?.takeIf { goalFraction != null },
        goalFraction = goalFraction,
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
      if (showYAxis) {
        EmptyGraphYLabels(yTicks = yTicks)
      }
    }
    EmptyGraphDayLabels(reserveYGutter = showYAxis)
  }
}

/** Y-axis ticks as (label, fractionFromTop). Top tick = yMax, bottom tick = yMin. */
private fun EmptyGraphRange.toYTicks(): List<Pair<String, Float>> {
  val span = yMax - yMin
  val ticks = buildList {
    var t = yMin
    while (t <= yMax + yStep * 0.001) {
      add(t)
      t += yStep
    }
  }
  return ticks.map { value -> value.roundToInt().toString() to ((yMax - value) / span).toFloat() }
}

private fun EmptyGraphRange.goalFractionFromTop(): Float? =
  goalValue?.takeIf { it in yMin..yMax }?.let { ((yMax - it) / (yMax - yMin)).toFloat() }

/** Plot area: gridlines + (optional) goal badge. No data line. */
@Composable
private fun EmptyGraphPlot(
  yTickFractions: List<Float>,
  goalValue: Double?,
  goalFraction: Float?,
  modifier: Modifier = Modifier,
) {
  val gridColor = MeTheme.colorScheme.utility
  BoxWithConstraints(modifier = modifier) {
    val plotHeight = maxHeight
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val stroke = 1.dp.toPx()
      val faint = gridColor.copy(alpha = 0.5f)

      // Top + right borders.
      drawLine(gridColor, Offset(0f, 0f), Offset(w, 0f), stroke)
      drawLine(gridColor, Offset(w, 0f), Offset(w, h), stroke)

      // Horizontal gridlines: one per Y tick when shown, else evenly spaced bands.
      val fractions = yTickFractions.ifEmpty { listOf(0.25f, 0.5f, 0.75f, 1f) }
      fractions.forEach { f -> drawLine(faint, Offset(0f, h * f), Offset(w, h * f), stroke) }

      // Vertical dotted gridlines at each day-column center.
      val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
      WeekDayLabels.indices.forEach { i ->
        val x = w * (i + 0.5f) / WeekDayLabels.size
        drawLine(faint, Offset(x, 0f), Offset(x, h), stroke, pathEffect = dash)
      }
    }

    if (goalFraction != null && goalValue != null) {
      Text(
        text = goalValue.roundToInt().toString(),
        style = MeTheme.typography.subHeading2,
        color = MeTheme.colorScheme.inverseAction,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .offset(x = MeTheme.spacing.sm, y = plotHeight * goalFraction - 11.dp)
          .background(SnapshotColors.GoalBadge, RoundedCornerShape(MeTheme.borderRadius.sm))
          .padding(horizontal = MeTheme.spacing.xs, vertical = MeTheme.spacing.x3s),
      )
    }
  }
}

/** Right-hand Y-axis labels positioned at each tick's fractional height. */
@Composable
private fun EmptyGraphYLabels(yTicks: List<Pair<String, Float>>) {
  BoxWithConstraints(modifier = Modifier.width(YGutterWidth).fillMaxHeight()) {
    val gutterHeight = maxHeight
    yTicks.forEach { (label, fraction) ->
      Text(
        text = label,
        style = MeTheme.typography.body2,
        color = MeTheme.colorScheme.textSubheading,
        modifier = Modifier
          .align(Alignment.TopStart)
          .offset(y = gutterHeight * fraction - 9.dp)
          .padding(start = MeTheme.spacing.xs),
      )
    }
  }
}

/** Bottom day labels (sun…sat), aligned to the plot area (excluding the Y gutter). */
@Composable
private fun EmptyGraphDayLabels(reserveYGutter: Boolean) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(end = if (reserveYGutter) YGutterWidth else 0.dp, top = MeTheme.spacing.x3s),
  ) {
    WeekDayLabels.forEach { day ->
      Text(
        text = day,
        style = MeTheme.typography.body2,
        color = MeTheme.colorScheme.textSubheading,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@PreviewTheme
@Composable
private fun EmptyDashboardGraphBarePreview() {
  MeAppTheme {
    EmptyDashboardGraph(modifier = Modifier.fillMaxWidth())
  }
}

@PreviewTheme
@Composable
private fun EmptyDashboardGraphWithGoalPreview() {
  MeAppTheme {
    EmptyDashboardGraph(
      modifier = Modifier.fillMaxWidth(),
      range = EmptyGraphDefaults.weightGoal(goalDisplay = 178.0, isKg = false),
    )
  }
}
