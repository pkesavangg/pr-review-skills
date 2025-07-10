package com.greatergoods.meapp.features.dashboard.components

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.features.dashboard.strings.DashboardMetricsStrings
import com.greatergoods.meapp.theme.MeTheme
import sh.calvin.reorderable.ReorderableCollectionItemScope

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
  isPlaceHolder: Boolean = false,
  onMetricClick: (Stat) -> Unit = {}
) {
  val contentHorizonalAlignment = if (stat.icon == null) Alignment.CenterHorizontally else Alignment.Start
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
        .padding(vertical = MeTheme.spacing.sm),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (stat.icon != null) {
        AppIcon(
          id = stat.icon,
          contentDescription = stat.label,
          tintColor = MeTheme.colorScheme.streak,
        )
      }
      Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = contentHorizonalAlignment,
      ) {
        Text(
          text = buildString {
            if (stat.valuePrefix != null) {
              append(stat.valuePrefix)
            }
            if (stat.value != null) {
              append(formatStatValue(stat.value))
            } else {
              append("---")
            }
          },
          style = MeTheme.typography.heading4,
          color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textHeading,
        )
        Text(
          text = stat.label.plus(" ").plus(stat.unit ?: "").lowercase(),
          style = MeTheme.typography.subHeading2,
          color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textSubheading,
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

  BadgedBox(
    badge = {
      if (inEditMode && !isDragging) {
        Badge(
          containerColor = MeTheme.colorScheme.inverseAction,
          contentColor = Color.Transparent,
          modifier = Modifier
            .size(24.dp)
            .clickable { onBadgeClick() }
            .border(1.dp, MeTheme.colorScheme.iconPrimary, CircleShape),
        ) {
          Icon(
            imageVector =
              if (isVisible)
                Icons.Default.Remove
              else
                Icons.Default.Add,
            contentDescription = if (isVisible) DashboardMetricsStrings.RemoveMetricDescription else DashboardMetricsStrings.AddMetricDescription,
            tint = MeTheme.colorScheme.iconPrimary,
            modifier = Modifier.size(14.dp),
          )
        }
      }
    },
    modifier = Modifier
      .graphicsLayer {
        rotationZ = if (inEditMode && isVisible) wiggleAngle else 0f
      },

    ) {
    StatCard(
      stat = stat,
      enabled = isSelected != null && !inEditMode,
      isVisible = isVisible,
      isSelected = isSelected ?: false,
      modifier = modifier,
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

