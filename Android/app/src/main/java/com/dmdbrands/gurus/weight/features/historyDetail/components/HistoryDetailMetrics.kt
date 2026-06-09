package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric.Companion.fromScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper.getMetrics
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun MetricItem(
  stat: Stat,
  modifier: Modifier = Modifier,
  index: Int,
  size: Int = 1,
  onMetricClick: () -> Unit = {},
) {
  val bgColor = StatHelper.getBgColor(index, size)
  var lastClickTime by remember { mutableStateOf(0L) }
  val debounceTime = 500L
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = {  val currentTime = android.os.SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime >= debounceTime) {
              lastClickTime = currentTime
              onMetricClick()
            } },
          onLongClick = { },
        )
        .background(bgColor)
        .padding(all = MeTheme.spacing.sm),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = stat.label,
      style = MeTheme.typography.body2,
      color = MeTheme.colorScheme.textBody,
    )
    Row {
      Text(
        text = buildString {
          if (stat.valuePrefix != null) {
            append(stat.valuePrefix)
            append(" ")
          }
          append(stat.value)
          if (stat.unit != null) {
            append(stat.unit)
          }
        },
        style = MeTheme.typography.body2,
        color = MeTheme.colorScheme.textBody,
      )
      Spacer(modifier = Modifier.width(MeTheme.spacing.x2s))
      if (stat.icon != null) {
        AppIcon(
          id = stat.icon,
          contentDescription = stat.label,
          onClick = null,
        )
      }
    }
  }
}

@Composable
private fun AnimatedMetricItem(
  stat: Stat,
  modifier: Modifier = Modifier,
  index: Int,
  size: Int = 1,
  onMetricClick: () -> Unit = {},
  isVisible: Boolean,
) {
  val alpha = remember { Animatable(0f) }

  LaunchedEffect(isVisible) {
    if (isVisible) {
      // Common delay for all sizes - balanced timing
      val delayMs = 50L  // Balanced timing to match manual entry

      delay(index * delayMs)
      alpha.animateTo(1f, animationSpec = tween(300))
    } else {
      alpha.snapTo(0f)
    }
  }

  MetricItem(
    stat = stat,
    modifier = modifier.graphicsLayer { this.alpha = alpha.value },
    index = index,
    size = size,
    onMetricClick = onMetricClick,
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeightHistoryDetailItemDetails(
  item: ScaleEntry,
  onEditEntry: () -> Unit = {},
) {
  val metrics = getMetrics(fromScaleEntry(item), showMetricIcon = true)
  val navBackStack = LocalNavBackStack.current
  val scope = rememberCoroutineScope()

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(MeTheme.colorScheme.primaryBackground),
  ) {
    metrics.forEachIndexed { index, metric ->
      AnimatedMetricItem(
        stat = metric,
        index = index,
        size = metrics.size,
        isVisible = true, // Always visible, animation handled internally
        onMetricClick = {
          val bodyMetric = fromScaleEntry(item)
          scope.launch {
            navBackStack.addRoute(
              AppRoute.Dashboard.MetricInfo(
                info = bodyMetric,
                key = if (metric.key is DashboardKey.Metric) metric.key.key else MetricKey.WEIGHT,
              ),
            )
          }
        },
      )
    }
    if (metrics.size % 2 != 0) {
      HorizontalDivider(
        thickness = 0.5.dp,
        color = MeTheme.colorScheme.utility,
      )
    }
    // Note (MOB-438) — always shown when expanded: the saved note or an add-note prompt,
    // with an edit pencil that opens the entry for editing.
    val note = item.scale.scaleEntry.note
    val hasNote = !note.isNullOrBlank()
    HorizontalDivider(
      thickness = 0.5.dp,
      color = MeTheme.colorScheme.utility,
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(MeTheme.spacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = if (hasNote) note.orEmpty() else HistoryItemStrings.NoNoteYet,
        style = MeTheme.typography.subHeading2,
        color = if (hasNote) MeTheme.colorScheme.textBody else MeTheme.colorScheme.textSubheading,
        modifier = Modifier.weight(1f),
      )
      AppIcon(
        id = AppIcons.Default.EditPencil,
        contentDescription = HistoryItemStrings.EditNoteContentDescription,
        onClick = { onEditEntry() },
        modifier = Modifier.padding(start = MeTheme.spacing.sm),
      )
    }
  }
}
