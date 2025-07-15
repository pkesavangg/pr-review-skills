package com.greatergoods.meapp.features.ScaleMetricsSetting.Screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.greatergoods.meapp.features.ScaleMetricsSetting.components.ScaleMetricItem
import com.greatergoods.meapp.features.common.components.AppDraggableList
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.borderRadius
import com.greatergoods.meapp.theme.MeTheme.spacing

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
) {

  // Separate state for each metric group
  var bodyMetricsState by remember(currentMetrics) {
    mutableStateOf(ScaleMetricsHelper.createOrderedMetrics(currentMetrics).first)
  }

  var otherMetricsState by remember(currentMetrics) {
    mutableStateOf(ScaleMetricsHelper.createOrderedMetrics(currentMetrics).second)
  }

  // Helper function to emit combined enabled metrics
  fun emitCombinedMetrics() {
    val enabledBodyMetrics = bodyMetricsState.filter { it.isEnabled }.map { it.key }
    val enabledOtherMetrics = otherMetricsState.filter { it.isEnabled }.map { it.key }
    val allEnabledKeys = enabledBodyMetrics + enabledOtherMetrics
    onMetricsChanged(allEnabledKeys)
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
      items = bodyMetricsState,
      onMove = { from, to ->
        val newList = bodyMetricsState.toMutableList()
        newList.add(to, newList.removeAt(from))
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
              bodyMetricsState =
                bodyMetricsState.map {
                  if (it.key == metric.key) it.copy(isEnabled = isEnabled) else it
                }
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
              otherMetricsState =
                otherMetricsState.map {
                  if (it.key == metric.key) it.copy(isEnabled = isEnabled) else it
                }
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
        "bmi",
        "bodyFatPercent",
        "musclePercent",
        "bodyWaterPercent",
        "heartRate",
        "bonePercent",
        "visceralFatLevel",
        "subcutaneousFatPercent",
        "proteinPercent",
        "skeletalMusclePercent",
        "bmr",
        "metabolicAge",
        "goalProgress",
        "dailyAverage",
        "weeklyAverage",
        "monthlyAverage",
      ),
      onMetricsChanged = { enabledKeys ->
        println("Enabled metrics: $enabledKeys")
      },
    )
  }
}
