package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.features.goal.components.GoalMilestoneDisplay
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Goal progress milestone card that shows weight progress with badge support.
 *
 * @param progress Progress data containing weight information
 * @param goal Goal data for weight targets
 * @param inEditMode Whether the dashboard is in edit mode
 * @param isVisible Whether the milestone is currently visible
 * @param modifier Modifier for the composable
 * @param onBadgeClick Callback when the badge is clicked
 */
@Composable
fun GoalProgressMilestoneCard(
  progress: Progress,
  inEditMode: Boolean,
  isVisible: Boolean = true,
  modifier: Modifier = Modifier,
  onBadgeClick: () -> Unit = {}
) {
  // Fine‑tunable badge offsets to match other cards' top‑right alignment
  val badgeOffsetX = (-28).dp
  val badgeOffsetY = MeTheme.spacing.sm
  // Defensive: computations aren't used for UI anymore; GoalMilestoneDisplay owns logic.
  // Keep locals only if needed for future UI extensions.

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
      if (inEditMode) {
        // Nudge the badge inwards so it visually centers on top-right like other cards
        Badge(
          containerColor = MeTheme.colorScheme.inverseAction,
          contentColor = Color.Transparent,
          modifier = Modifier
            .offset(x = badgeOffsetX, y = badgeOffsetY)
            .size(24.dp)
            .clickable { onBadgeClick() }
            .border(1.dp, MeTheme.colorScheme.iconPrimary, CircleShape),
        ) {
          Icon(
            imageVector = if (isVisible) Icons.Default.Remove else Icons.Default.Add,
            contentDescription = if (isVisible) "Remove goal progress" else "Add goal progress",
            tint = MeTheme.colorScheme.iconPrimary,
            modifier = Modifier.size(14.dp),
          )
        }
      }
    },
    modifier = modifier
      .graphicsLayer {
        rotationZ = if (inEditMode && isVisible) wiggleAngle else 0f
      },
  ) {
    Card(
      modifier = Modifier
        .wrapContentSize()
        .alpha(if (isVisible) 1f else 0.5f),
      colors = CardDefaults.cardColors(containerColor = MeTheme.colorScheme.primaryBackground),
    ) {
      // Reuse the shared GoalMilestoneDisplay to ensure consistent look and logic.
      // If we have the required account/goal context, render; otherwise, no-op.
      val account = progress.goal?.account
      if (account != null) {
        val latest = (progress.latest as? ScaleEntry)?.scale?.scaleEntry?.weight?.toDouble()
        GoalMilestoneDisplay(
          account = account,
          latestWeight = latest,
          modifier = Modifier
            .fillMaxWidth(),
        )
      }
    }
  }
}
