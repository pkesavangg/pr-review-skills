package com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.components.ScaleMetricItem
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.model.ScaleMetric
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.model.ScaleMetricKeys
import com.dmdbrands.gurus.weight.features.common.components.AppDraggableList
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import sh.calvin.reorderable.Scroller

/**
 * Display metrics screen with reorderable list of scale metrics.
 * Metrics are grouped into body composition metrics (with icons) and other metrics (text-only).
 * Each group can be reordered independently, but the final output combines all enabled metrics.
 *
 * @param scale The device scale containing display metrics and configuration.
 * @param onMetricsChanged Callback when metrics are reordered or toggled, returns ordered list of enabled metric keys.
 * @param modifier Modifier for the composable.
 * @param onUpdateScaleMode Callback when update button is clicked for scale mode notifications.
 */
@Composable
fun ScaleMetricsSettingScreen(
  currentMetrics: List<String> = emptyList(),
  onMetricsChanged: (List<String>) -> Unit = {},
  modifier: Modifier = Modifier,
  scrollState : ScrollableState? = null,
  includeHeartRate: Boolean = true,
  showAllMetrics: Boolean = true,
) {

  // Separate state for each metric group - initialize once, update via LaunchedEffect only when needed
  var bodyMetricsState by remember {
    val (bodyMetrics, _) = ScaleMetricsHelper.createOrderedMetrics(currentMetrics)
    val updatedBodyMetrics = bodyMetrics.map { metric ->
      if (metric.key == ScaleMetricKeys.HEART_RATE) {
        metric.copy(isIncluded = includeHeartRate)
      } else {
        metric
      }
    }

    // If heartRate is excluded, move it to the end
    val reorderedMetrics = if (!includeHeartRate) {
      val heartRateMetric = updatedBodyMetrics.find { it.key == ScaleMetricKeys.HEART_RATE }
      val others = updatedBodyMetrics.filterNot { it.key == ScaleMetricKeys.HEART_RATE }
      if (heartRateMetric != null) others + heartRateMetric else updatedBodyMetrics
    } else {
      updatedBodyMetrics
    }
    mutableStateOf(reorderedMetrics)
  }

  // Displayed list respects showAllMetrics, but state remains full
  val displayedBodyMetrics = if (showAllMetrics) {
    bodyMetricsState
  } else {
    bodyMetricsState.filter { it.key == ScaleMetricKeys.BMI }
  }

  var otherMetricsState by remember {
    mutableStateOf(ScaleMetricsHelper.createOrderedMetrics(currentMetrics).second)
  }

  // Sync state when currentMetrics or includeHeartRate changes from outside (only if different from our current state)
  LaunchedEffect(currentMetrics, includeHeartRate) {
    // Calculate what our current state would emit (only enabled metrics)
    val currentEnabledMetrics = bodyMetricsState.filter { it.isEnabled }.map { it.key } +
        otherMetricsState.filter { it.isEnabled }.map { it.key }

    // Check if heart rate metric's isIncluded matches includeHeartRate parameter
    val heartRateMetric = bodyMetricsState.find { it.key == ScaleMetricKeys.HEART_RATE }
    val heartRateIncludedMatches = heartRateMetric?.isIncluded == includeHeartRate

    // Only sync if currentMetrics is different from what we would emit OR if heart rate inclusion state doesn't match
    // This prevents reset loops when onMetricsChanged updates the parent
    // Use content comparison to check if lists are different (order-independent for safety)
    val metricsMatch = currentEnabledMetrics.size == currentMetrics.size &&
        currentEnabledMetrics.all { it in currentMetrics } &&
        currentMetrics.all { it in currentEnabledMetrics } &&
        heartRateIncludedMatches

    if (!metricsMatch) {
      val (bodyMetrics, otherMetrics) = ScaleMetricsHelper.createOrderedMetrics(currentMetrics)
      val updatedBodyMetrics = bodyMetrics.map { metric ->
        if (metric.key == ScaleMetricKeys.HEART_RATE) {
          metric.copy(isIncluded = includeHeartRate)
        } else {
          metric
        }
      }

      // If heartRate is excluded, move it to the end
      val reorderedMetrics = if (!includeHeartRate) {
        val heartRateMetric = updatedBodyMetrics.find { it.key == ScaleMetricKeys.HEART_RATE }
        val others = updatedBodyMetrics.filterNot { it.key == ScaleMetricKeys.HEART_RATE }
        if (heartRateMetric != null) others + heartRateMetric else updatedBodyMetrics
      } else {
        updatedBodyMetrics
      }

      bodyMetricsState = reorderedMetrics
      otherMetricsState = otherMetrics
    }
  }

  // Helper function to emit combined enabled metrics
  fun emitCombinedMetrics() {
    val enabledBodyMetrics = bodyMetricsState.filter { it.isEnabled }.map { it.key }
    val enabledOtherMetrics = otherMetricsState.filter { it.isEnabled }.map { it.key }
    val allEnabledKeys = enabledBodyMetrics + enabledOtherMetrics
    onMetricsChanged(allEnabledKeys)
  }

  /**
   * Handles toggle operation for a metric item gracefully.
   * When toggled off, moves the item to the end of the list.
   * When toggled on, moves the item to the bottom of the enabled list.
   *
   * @param metricsList The mutable list of metrics to update.
   * @param metricKey The key of the metric to toggle.
   * @param isEnabled The new enabled state for the metric.
   * @return The updated list with the metric repositioned appropriately.
   */
  fun handleMetricToggle(
    metricsList: MutableList<ScaleMetric>,
    metricKey: String,
    isEnabled: Boolean,
  ): List<ScaleMetric> {
    val updatedList = metricsList.toMutableList()
    val itemIndex = updatedList.indexOfFirst { it.key == metricKey }
    if (itemIndex == -1) {
      // Metric not found, return original list
      return updatedList
    }
    val updatedItem = updatedList[itemIndex].copy(isEnabled = isEnabled)
    updatedList.removeAt(itemIndex)
    if (!isEnabled) {
      // Toggled off: move to the end of the list
      updatedList.add(updatedItem)
    } else {
      // Toggled on: move to the bottom of the enabled list
      val lastEnabledIndex = updatedList.indexOfLast { it.isEnabled }
      if (lastEnabledIndex != -1) {
        // Found enabled items: insert after the last enabled one (bottom of enabled list)
        updatedList.add(lastEnabledIndex + 1, updatedItem)
      } else {
        // No enabled items exist: place at the beginning
        updatedList.add(0, updatedItem)
      }
    }
    return updatedList
  }

  Column(
    modifier = modifier
      .fillMaxSize(),
  ) {
    // Body Composition Metrics Section
    AppDraggableList(
      modifier = Modifier
        .clip(shape = RoundedCornerShape(borderRadius.sm))
        .heightIn(max = 600.dp),
      scrollState = scrollState,
      items = displayedBodyMetrics,
      onMove = { from, to ->
        val newList = bodyMetricsState.toMutableList()
        val itemToMove = displayedBodyMetrics[from]
        val toIndexInState = bodyMetricsState.indexOf(displayedBodyMetrics[to])
        newList.remove(itemToMove)
        newList.add(toIndexInState, itemToMove)
        bodyMetricsState = newList
        emitCombinedMetrics()
      },
      keySelector = { "body_${it.key}" },
      itemContent = { metric ->
        DraggableItem(
          isDraggable = metric.isEnabled,
        ) { isDragging, modifier ->
          ScaleMetricItem(
            metric = metric,
            isDragging = isDragging,
            dragHandleModifier = modifier,
            onToggle = { isEnabled ->
              bodyMetricsState = handleMetricToggle(
                metricsList = bodyMetricsState.toMutableList(),
                metricKey = metric.key,
                isEnabled = isEnabled,
              )
              emitCombinedMetrics()
            },
          )
        }
      },
    )

    Spacer(Modifier.height(spacing.md))

    // Other Metrics Section (Goals and Averages)
    AppDraggableList(
      modifier = Modifier
        .clip(shape = RoundedCornerShape(borderRadius.sm))
        .heightIn(max = 200.dp),
      scrollState = scrollState,
      items = otherMetricsState,
      onMove = { from, to ->
        val newList = otherMetricsState.toMutableList()
        newList.add(to, newList.removeAt(from))
        otherMetricsState = newList
        emitCombinedMetrics()
      },
      keySelector = { "other_${it.key}" },
      itemContent = { metric ->
        DraggableItem(
          isDraggable = metric.isEnabled,
        ) { isDragging, modifier ->
          ScaleMetricItem(
            metric = metric,
            isDragging = isDragging,
            dragHandleModifier = modifier,
            onToggle = { isEnabled ->
              otherMetricsState = handleMetricToggle(
                metricsList = otherMetricsState.toMutableList(),
                metricKey = metric.key,
                isEnabled = isEnabled,
              )
              emitCombinedMetrics()
            },
          )
        }
      },
    )
  }
}

@PreviewTheme
@Composable
fun DisplayMetricsScreenPreview() {
  MeAppTheme {
    ScaleMetricsSettingScreen(
      currentMetrics = listOf(
        ScaleMetricKeys.BMI,
        ScaleMetricKeys.BODY_FAT_PERCENT,
        ScaleMetricKeys.MUSCLE_PERCENT,
        ScaleMetricKeys.BODY_WATER_PERCENT,
        ScaleMetricKeys.HEART_RATE,
        ScaleMetricKeys.BONE_PERCENT,
        ScaleMetricKeys.VISCERAL_FAT_LEVEL,
        ScaleMetricKeys.SUBCUTANEOUS_FAT_PERCENT,
        ScaleMetricKeys.PROTEIN_PERCENT,
        ScaleMetricKeys.SKELETAL_MUSCLE_PERCENT,
        ScaleMetricKeys.BMR,
        ScaleMetricKeys.METABOLIC_AGE,
        ScaleMetricKeys.GOAL_PROGRESS,
        ScaleMetricKeys.DAILY_AVERAGE,
        ScaleMetricKeys.WEEKLY_AVERAGE,
        ScaleMetricKeys.MONTHLY_AVERAGE,
      ),
      onMetricsChanged = { _ -> },
      includeHeartRate = false,
    )
  }
}
