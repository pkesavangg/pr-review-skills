package com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.Screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.Helper.DeviceMetricsHelper
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.components.DeviceMetricItem
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.model.DeviceMetric
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.model.DeviceMetricKeys
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
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
fun DeviceMetricsSettingScreen(
  currentMetrics: List<String> = emptyList(),
  onMetricsChanged: (List<String>) -> Unit = {},
  modifier: Modifier = Modifier,
  scrollState : ScrollableState? = null,
  includeHeartRate: Boolean = true,
  showAllMetrics: Boolean = true,
) {

  // Separate state for each metric group - initialize once, update via LaunchedEffect only when needed
  var bodyMetricsState by remember {
    mutableStateOf(buildBodyMetrics(currentMetrics, includeHeartRate))
  }

  // Displayed list respects showAllMetrics, but state remains full
  val displayedBodyMetrics = if (showAllMetrics) {
    bodyMetricsState
  } else {
    bodyMetricsState.filter { it.key == DeviceMetricKeys.BMI }
  }

  var otherMetricsState by remember {
    mutableStateOf(DeviceMetricsHelper.createOrderedMetrics(currentMetrics).second)
  }

  // Sync state when currentMetrics or includeHeartRate changes from outside (only if different from our current state)
  LaunchedEffect(currentMetrics, includeHeartRate) {
    if (!metricsAreInSync(bodyMetricsState, otherMetricsState, currentMetrics, includeHeartRate)) {
      bodyMetricsState = buildBodyMetrics(currentMetrics, includeHeartRate)
      otherMetricsState = DeviceMetricsHelper.createOrderedMetrics(currentMetrics).second
    }
  }

  // Emits the combined list of currently-enabled metrics to the parent.
  val emitMetrics = {
    emitCombinedMetrics(bodyMetricsState, otherMetricsState, onMetricsChanged)
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.DeviceMetrics.SettingScreenRoot),
  ) {
    // Body Composition Metrics Section
    BodyMetricsList(
      bodyMetricsState = bodyMetricsState,
      displayedBodyMetrics = displayedBodyMetrics,
      scrollState = scrollState,
      onStateChange = { bodyMetricsState = it },
      onEmit = emitMetrics,
    )

    Spacer(Modifier.height(spacing.md))

    // Other Metrics Section (Goals and Averages)
    OtherMetricsList(
      otherMetricsState = otherMetricsState,
      scrollState = scrollState,
      onStateChange = { otherMetricsState = it },
      onEmit = emitMetrics,
    )
  }
}

@Composable
private fun BodyMetricsList(
  bodyMetricsState: List<DeviceMetric>,
  displayedBodyMetrics: List<DeviceMetric>,
  scrollState: ScrollableState?,
  onStateChange: (List<DeviceMetric>) -> Unit,
  onEmit: () -> Unit,
) {
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
      onStateChange(newList)
      onEmit()
    },
    keySelector = { "body_${it.key}" },
    itemContent = { metric ->
      DraggableItem(
        isDraggable = metric.isEnabled,
      ) { isDragging, modifier ->
        DeviceMetricItem(
          metric = metric,
          isDragging = isDragging,
          dragHandleModifier = modifier,
          onToggle = { isEnabled ->
            onStateChange(
              handleMetricToggle(
                metricsList = bodyMetricsState.toMutableList(),
                metricKey = metric.key,
                isEnabled = isEnabled,
              ),
            )
            onEmit()
          },
        )
      }
    },
  )
}

@Composable
private fun OtherMetricsList(
  otherMetricsState: List<DeviceMetric>,
  scrollState: ScrollableState?,
  onStateChange: (List<DeviceMetric>) -> Unit,
  onEmit: () -> Unit,
) {
  AppDraggableList(
    modifier = Modifier
      .clip(shape = RoundedCornerShape(borderRadius.sm))
      .heightIn(max = 200.dp),
    scrollState = scrollState,
    items = otherMetricsState,
    onMove = { from, to ->
      val newList = otherMetricsState.toMutableList()
      newList.add(to, newList.removeAt(from))
      onStateChange(newList)
      onEmit()
    },
    keySelector = { "other_${it.key}" },
    itemContent = { metric ->
      DraggableItem(
        isDraggable = metric.isEnabled,
      ) { isDragging, modifier ->
        DeviceMetricItem(
          metric = metric,
          isDragging = isDragging,
          dragHandleModifier = modifier,
          onToggle = { isEnabled ->
            onStateChange(
              handleMetricToggle(
                metricsList = otherMetricsState.toMutableList(),
                metricKey = metric.key,
                isEnabled = isEnabled,
              ),
            )
            onEmit()
          },
        )
      }
    },
  )
}

/**
 * Builds the ordered body-composition metrics for the given source keys, applying the
 * [includeHeartRate] flag and moving the heart-rate metric to the end when excluded.
 */
private fun buildBodyMetrics(
  currentMetrics: List<String>,
  includeHeartRate: Boolean,
): List<DeviceMetric> {
  val (bodyMetrics, _) = DeviceMetricsHelper.createOrderedMetrics(currentMetrics)
  val updatedBodyMetrics = bodyMetrics.map { metric ->
    if (metric.key == DeviceMetricKeys.HEART_RATE) {
      metric.copy(isIncluded = includeHeartRate)
    } else {
      metric
    }
  }

  // If heartRate is excluded, move it to the end
  return if (!includeHeartRate) {
    val heartRateMetric = updatedBodyMetrics.find { it.key == DeviceMetricKeys.HEART_RATE }
    val others = updatedBodyMetrics.filterNot { it.key == DeviceMetricKeys.HEART_RATE }
    if (heartRateMetric != null) others + heartRateMetric else updatedBodyMetrics
  } else {
    updatedBodyMetrics
  }
}

/**
 * Returns true when the local metric state already matches the external [currentMetrics] and
 * [includeHeartRate] inputs, so no resync is required (prevents reset loops).
 */
private fun metricsAreInSync(
  bodyMetricsState: List<DeviceMetric>,
  otherMetricsState: List<DeviceMetric>,
  currentMetrics: List<String>,
  includeHeartRate: Boolean,
): Boolean {
  // Calculate what our current state would emit (only enabled metrics)
  val currentEnabledMetrics = bodyMetricsState.filter { it.isEnabled }.map { it.key } +
      otherMetricsState.filter { it.isEnabled }.map { it.key }

  // Check if heart rate metric's isIncluded matches includeHeartRate parameter
  val heartRateMetric = bodyMetricsState.find { it.key == DeviceMetricKeys.HEART_RATE }
  val heartRateIncludedMatches = heartRateMetric?.isIncluded == includeHeartRate

  // Use content comparison to check if lists are different (order-independent for safety)
  return currentEnabledMetrics.size == currentMetrics.size &&
      currentEnabledMetrics.all { it in currentMetrics } &&
      currentMetrics.all { it in currentEnabledMetrics } &&
      heartRateIncludedMatches
}

// Helper function to emit combined enabled metrics
private fun emitCombinedMetrics(
  bodyMetricsState: List<DeviceMetric>,
  otherMetricsState: List<DeviceMetric>,
  onMetricsChanged: (List<String>) -> Unit,
) {
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
private fun handleMetricToggle(
  metricsList: MutableList<DeviceMetric>,
  metricKey: String,
  isEnabled: Boolean,
): List<DeviceMetric> {
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

@PreviewTheme
@Composable
fun DisplayMetricsScreenPreview() {
  MeAppTheme {
    DeviceMetricsSettingScreen(
      currentMetrics = listOf(
        DeviceMetricKeys.BMI,
        DeviceMetricKeys.BODY_FAT_PERCENT,
        DeviceMetricKeys.MUSCLE_PERCENT,
        DeviceMetricKeys.BODY_WATER_PERCENT,
        DeviceMetricKeys.HEART_RATE,
        DeviceMetricKeys.BONE_PERCENT,
        DeviceMetricKeys.VISCERAL_FAT_LEVEL,
        DeviceMetricKeys.SUBCUTANEOUS_FAT_PERCENT,
        DeviceMetricKeys.PROTEIN_PERCENT,
        DeviceMetricKeys.SKELETAL_MUSCLE_PERCENT,
        DeviceMetricKeys.BMR,
        DeviceMetricKeys.METABOLIC_AGE,
        DeviceMetricKeys.GOAL_PROGRESS,
        DeviceMetricKeys.DAILY_AVERAGE,
        DeviceMetricKeys.WEEKLY_AVERAGE,
        DeviceMetricKeys.MONTHLY_AVERAGE,
      ),
      onMetricsChanged = { _ -> },
      includeHeartRate = false,
    )
  }
}
