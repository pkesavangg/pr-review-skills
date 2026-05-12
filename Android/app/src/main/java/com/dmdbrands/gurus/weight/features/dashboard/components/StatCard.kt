package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Composable for displaying a single metric item in the dashboard metrics grid.
 */
@Composable
internal fun StatCard(
  stat: Stat,
  enabled: Boolean = true,
  isVisible: Boolean = true,
  isSelected: Boolean = false,
  modifier: Modifier = Modifier,
  isFromSetup: Boolean = false,
  isPlaceHolder: Boolean = false,
  onMetricLongClick: (Stat) -> Unit = {},
  onMetricClick: (Stat) -> Unit = {}
) {
  val contentHorizonalAlignment =
    if (stat.icon == null || stat.value == null) Alignment.CenterHorizontally else Alignment.Start
  val hideMetricData = isFromSetup && stat.key is DashboardKey.Metric
  val metricLabel =
    if (hideMetricData) stat.label.lowercase() else stat.label.plus(" ").plus(stat.unit ?: "").lowercase()
  val metricData = buildString {
    if (isFromSetup && stat.key is DashboardKey.Milestone) {
      append("+/-")
    } else {
      if (stat.valuePrefix != null) {
        append(stat.valuePrefix)
      }
      if (stat.value != null) {
        append(formatStatValue(stat.value))
        if (stat.valueSuffix != null) {
          append(" " + stat.valueSuffix)
        }
      } else {
        // Handle null values based on key type
        when {
          !isFromSetup && stat.key is DashboardKey.Milestone && stat.key.key == MilestoneKey.CURRENT_STREAK ->
            append("0 days")

          !isFromSetup && stat.key is DashboardKey.Milestone && !isStreakMilestone(stat) ->
            append("0")

          else ->
            append("--")
        }
      }
    }
  }

  val shouldShowMetricData = if (isFromSetup) {
    // In setup mode, do NOT show for dashboard metrics and streak milestones
    !(stat.key is DashboardKey.Metric || isStreakMilestone(stat))
  } else {
    // Not in setup mode, always show
    true
  }
  val textAlignment = if (stat.key is DashboardKey.Milestone && isStreakMilestone(stat)) {
    TextAlign.Start
  } else {
    TextAlign.Center
  }
  Card(
    modifier = Modifier
      .fillMaxSize()
      .onLongPress(
        key = stat,
        enabled = enabled,
        onClick = {
          onMetricClick(stat)
        },
        onLongPress = {
          onMetricLongClick(stat)
        },
      )
      .alpha(if (isVisible && !isPlaceHolder) 1f else 0.5f), // 50% opacity when not visible,
    shape = RoundedCornerShape(MeTheme.borderRadius.sm),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected && isVisible && !isPlaceHolder) MeTheme.colorScheme.secondaryAction else MeTheme.colorScheme.inverseAction,
      disabledContainerColor = if (isSelected && isVisible && !isPlaceHolder) MeTheme.colorScheme.secondaryAction else MeTheme.colorScheme.inverseAction,
    ),
  ) {
    Row(
      modifier = modifier
        .fillMaxSize()
        .padding(vertical = MeTheme.spacing.sm)
        .then(if (isFromSetup) Modifier.height(55.dp) else Modifier)
        .then(if (isStreakMilestone(stat)) Modifier.padding(horizontal = MeTheme.spacing.sm) else Modifier),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (stat.icon != null && stat.key is DashboardKey.Milestone) {
        Row(
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AppIcon(
            id = stat.icon,
            contentDescription = stat.label,
            tintColor = if (isVisible) MeTheme.colorScheme.streak else MeTheme.colorScheme.iconSecondary,
          )
        }
      }
      Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = contentHorizonalAlignment,
        modifier = Modifier
          .fillMaxSize()
          .weight(2f),
      ) {
        if (stat.icon != null && hideMetricData) {
          AppIcon(
            id = stat.icon,
            contentDescription = stat.label,
            modifier = Modifier.size(24.dp),
            type = AppIconType.Secondary,
            enabled = isVisible,
          )
          Spacer(modifier = Modifier.size(MeTheme.spacing.x2s))
        }
        if (shouldShowMetricData) {
          Text(
            text = metricData,
            style = MeTheme.typography.heading4,
            color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textHeading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = metricLabel,
          style = MeTheme.typography.subHeading2,
          textAlign = textAlignment,
          color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textSubheading,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun AnimatedStatCard(
  stat: Stat,
  inEditMode: Boolean,
  isDragging: Boolean = false,
  isSelected: Boolean? = false,
  isVisible: Boolean = true,
  isFromSetup: Boolean = true,
  modifier: Modifier = Modifier,
  onBadgeClick: () -> Unit = {},
  canLongPress: Boolean = false,
  onLongClick: (Stat) -> Unit = {},
  onClick: () -> Unit = {},
) {
  // Wiggle animation
  val infiniteTransition = rememberInfiniteTransition()
  val wiggleAngle by infiniteTransition.animateFloat(
    initialValue = -1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 100, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
  )
  if (isVisible) MeTheme.colorScheme.secondaryAction else MeTheme.colorScheme.iconPrimary
  val dragCardShadow = if (isDragging) {
    Modifier.dropShadow(
      shape = RoundedCornerShape(MeTheme.spacing.x6s),
      shadow = Shadow(
        radius = MeTheme.spacing.sm,
        spread = 0.dp,
        color = MeTheme.colorScheme.glow,
        offset = DpOffset(x = 0.dp, 0.dp),
      ),
    )
  } else Modifier

  BadgedBox(
    badge = {
      if (inEditMode && !isDragging) {
        Badge(
          containerColor = Color.Transparent,
          contentColor = Color.Transparent,
          modifier = Modifier
            .padding(0.dp)
            .offset((-6).dp, (6).dp)
            .size(32.dp)
            .clickable { onBadgeClick() },
        ) {
          Icon(
            painter = painterResource(id = if (isVisible) AppIcons.Filled.MinusCircle else AppIcons.Filled.AddCircle),
            contentDescription = if (isVisible) DashboardString.RemoveMetricDescription else DashboardString.AddMetricDescription,
            tint = Color.Unspecified,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    },
    modifier = Modifier
      .graphicsLayer {
        rotationZ = if (inEditMode && isVisible) wiggleAngle else 0f
      }
      .then(dragCardShadow),
  ) {
    StatCard(
      stat = stat,
      enabled = (isSelected != null || canLongPress) && !inEditMode,
      isVisible = isVisible,
      isSelected = isSelected == true && !inEditMode,
      modifier = modifier,
      isFromSetup = isFromSetup,
      onMetricLongClick = onLongClick,
    ) {
      onClick()
    }
  }
}

/**
 * Formats a stat value based on its type for display.
 * Handles different data types gracefully and provides consistent formatting.
 */
private fun formatStatValue(value: Any?): String {
  return when (value) {
    null -> "--"
    is String -> value
    is Number -> {
      when (value) {
        is Double -> if (value == value.toInt().toDouble()) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
        is Float -> if (value == value.toInt().toFloat()) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
        else -> value.toString()
      }
    }

    is Boolean -> if (value) "Yes" else "No"
    is Map<*, *> -> value.entries.joinToString(", ") { "${it.key}: ${it.value}" }
    is List<*> -> value.joinToString(", ") { it.toString() }
    else -> value.toString()
  }
}
