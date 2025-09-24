package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.reorderable.ReorderableCollectionItemScope
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
  onMetricClick: (Stat) -> Unit = {}
) {
  val contentHorizonalAlignment =
    if (stat.icon == null || stat.value == null) Alignment.CenterHorizontally else Alignment.Start
  val hideMetricData = isFromSetup && stat.key is DashboardKey.Metric
  val metricLabel =
    if (hideMetricData) stat.label.lowercase() else stat.label.plus(" ").plus(stat.unit ?: "").lowercase()
  val metricData = buildString {
    if (stat.valuePrefix != null) {
      append(stat.valuePrefix)
    }
    if (stat.value != null) {
      append(formatStatValue(stat.value))
      if (stat.valueSuffix != null) {
        append(" " + stat.valueSuffix)
      }
    } else {
      if (isFromSetup && stat.key is DashboardKey.Milestone) append("+/-") else append("---")
    }
  }

  val shouldShowMetricData = if (isFromSetup) {
    // In setup mode, do NOT show for dashboard metrics and streak milestones
    !(stat.key is DashboardKey.Metric || isStreakMilestone(stat) )
  } else {
    // Not in setup mode, always show
    true
  }
  Card(
    modifier = Modifier
      .fillMaxSize()
      .alpha(if (isVisible && !isPlaceHolder) 1f else 0.5f), // 50% opacity when not visible,
    shape = RoundedCornerShape(MeTheme.borderRadius.sm),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected && isVisible && !isPlaceHolder) MeTheme.colorScheme.secondaryAction else MeTheme.colorScheme.inverseAction,
      disabledContainerColor = if (isSelected && isVisible && !isPlaceHolder) MeTheme.colorScheme.secondaryAction else MeTheme.colorScheme.inverseAction,
    ),
    enabled = enabled,
    onClick = { onMetricClick(stat) },
  ) {
    Row(
      modifier = modifier
        .fillMaxSize()
        .padding(vertical = MeTheme.spacing.sm)
        .then(
          if (isFromSetup) Modifier.height(55.dp) else Modifier,
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (stat.icon != null && stat.key is DashboardKey.Milestone) {
        AppIcon(
          id = stat.icon,
          contentDescription = stat.label,
          tintColor = MeTheme.colorScheme.streak,
        )
        Spacer(modifier = Modifier.size(MeTheme.spacing.xs))
      }
      Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = contentHorizonalAlignment,
      ) {
        if (stat.icon != null && hideMetricData) {
          AppIcon(
            id = stat.icon,
            contentDescription = stat.label,
            modifier = Modifier.size(24.dp),
            tintColor = MeTheme.colorScheme.iconPrimary,
          )
          Spacer(modifier = Modifier.size(MeTheme.spacing.xs))
        }
        if (shouldShowMetricData) {
          Text(
            text = metricData,
            style = MeTheme.typography.heading4,
            color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textHeading,
          )
        }
        Text(
          text = metricLabel,
          style = MeTheme.typography.subHeading2,
          textAlign = TextAlign.Center,
          color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textSubheading,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
  onClick: () -> Unit = {},
  reorderableScope: ReorderableCollectionItemScope? = null
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
  val iconTint = if (isVisible) MeTheme.colorScheme.secondaryAction else MeTheme.colorScheme.iconPrimary
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
          containerColor = MeTheme.colorScheme.inverseAction,
          contentColor = Color.Transparent,
          modifier = Modifier
            .offset(0.dp, 4.dp)
            .size(24.dp)
            .clickable { onBadgeClick() }
            .border(2.dp, iconTint, CircleShape),
        ) {
          Icon(
            painter = painterResource(id = if (isVisible) AppIcons.Default.Minus else AppIcons.Default.Plus),
            contentDescription = if (isVisible) DashboardString.RemoveMetricDescription else DashboardString.AddMetricDescription,
            tint = iconTint,
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
      enabled = isSelected != null && !inEditMode,
      isVisible = isVisible,
      isSelected = isSelected ?: false,
      modifier = modifier,
      isFromSetup = isFromSetup,
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
    null -> "---"
    is String -> value
    is Number -> {
      when (value) {
        is Double -> if (value == value.toInt().toDouble()) value.toInt().toString() else String.format("%.1f", value)
        is Float -> if (value == value.toInt().toFloat()) value.toInt().toString() else String.format("%.1f", value)
        else -> value.toString()
      }
    }

    is Boolean -> if (value) "Yes" else "No"
    is Map<*, *> -> value.entries.joinToString(", ") { "${it.key}: ${it.value}" }
    is List<*> -> value.joinToString(", ") { it.toString() }
    else -> value.toString()
  }
}