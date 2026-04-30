package com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.model.ScaleMetric
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.model.scaleMetrics
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.AppToggle
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Individual display metric item composable.
 *
 * @param metric The scale metric to display.
 * @param isDragging Whether the item is currently being dragged.
 * @param onToggle Callback when the toggle state changes.
 * @param modifier Modifier for the composable.
 */
@Composable
fun ScaleMetricItem(
  metric: ScaleMetric,
  isDragging: Boolean,
  onToggle: (Boolean) -> Unit,
  dragHandleModifier: Modifier = Modifier,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RectangleShape,
    colors =
      CardDefaults.cardColors(
        containerColor = MeTheme.colorScheme.primaryBackground,
      ),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding( horizontal = spacing.sm),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
      ) {
        // Metric icon (only for metrics that have icons)
        metric.metricIcon?.let { iconRes ->
          AppIcon(
            id = iconRes,
            contentDescription = metric.label,
            modifier = Modifier.size(24.dp),
            type = if (metric.isIncluded && metric.isEnabled) AppIconType.Primary else AppIconType.Tertiary,
            enabled = metric.isIncluded
          )
        }

        // Metric label
        AppText(
          text = metric.label,
          textType = TextType.Body,
          enabled = metric.isIncluded,
        )
      }

      Row(
        modifier = Modifier
          .then(if (!metric.isIncluded) Modifier.padding(vertical =  spacing.md) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
      ) {
        // Drag handle
        if(metric.isEnabled && metric.isIncluded) {
          AppIcon(
            id = AppIcons.Default.DragHandler,
            contentDescription = "Drag handle",
            modifier = dragHandleModifier.size(24.dp),
            type = AppIconType.Tertiary,
          )
        }

        // Toggle switch
        if(metric.isIncluded) {
          AppToggle(
            checked = metric.isEnabled,
            onCheckedChange = onToggle
          )
        }
      }
    }
    HorizontalDivider(
      color = MeTheme.colorScheme.utility,
      thickness = .5.dp,
    )
  }
}

@PreviewTheme
@Composable
private fun DisplayMetricItemPreview() {
  MeAppTheme {
    Column {
      // Enabled metric
      ScaleMetricItem(
        metric = scaleMetrics.first(),
        isDragging = false,
        onToggle = {},
      )
      Spacer(Modifier.height(spacing.md))
      // Dragging metric
      ScaleMetricItem(
        metric = scaleMetrics.first().copy(isIncluded = false),
        isDragging = true,
        onToggle = {},
      )
      Spacer(Modifier.height(spacing.md))
      // Disabled metric (drag handle visible but dimmed)
      ScaleMetricItem(
        metric = scaleMetrics.first().copy(isEnabled = false),
        isDragging = false,
        onToggle = {},
      )
    }
  }
}
