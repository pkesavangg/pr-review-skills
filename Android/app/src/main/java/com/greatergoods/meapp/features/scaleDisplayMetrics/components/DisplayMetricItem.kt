package com.greatergoods.meapp.features.scaleDisplayMetrics.components

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
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.AppIconType
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.AppToggle
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.scaleDisplayMetrics.model.ScaleMetric
import com.greatergoods.meapp.features.scaleDisplayMetrics.model.scaleMetrics
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * Individual display metric item composable.
 *
 * @param metric The scale metric to display.
 * @param isDragging Whether the item is currently being dragged.
 * @param onToggle Callback when the toggle state changes.
 * @param modifier Modifier for the composable.
 */
@Composable
fun DisplayMetricItem(
  metric: ScaleMetric,
  isDragging: Boolean,
  onToggle: (Boolean) -> Unit,
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
          .padding(vertical = spacing.xs, horizontal = spacing.sm),
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
      ) {
        // Drag handle
        AppIcon(
          id = AppIcons.Default.DragHandler,
          contentDescription = "Drag handle",
          modifier = Modifier.size(24.dp),
          type = AppIconType.Tertiary,
        )

        // Toggle switch
        AppToggle(
          checked = metric.isEnabled,
          onCheckedChange = onToggle,
        )
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
      DisplayMetricItem(
        metric = scaleMetrics.first(),
        isDragging = false,
        onToggle = {},
      )
      Spacer(Modifier.height(spacing.md))
      // Dragging metric
      DisplayMetricItem(
        metric = scaleMetrics.first(),
        isDragging = true,
        onToggle = {},
      )
      Spacer(Modifier.height(spacing.md))
      // Disabled metric (drag handle visible but dimmed)
      DisplayMetricItem(
        metric = scaleMetrics.first().copy(isEnabled = false),
        isDragging = false,
        onToggle = {},
      )
    }
  }
}
