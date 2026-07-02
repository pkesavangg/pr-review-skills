package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.power.powerSaveAwareInfiniteFloat
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.features.goal.components.EmptyGoal
import com.dmdbrands.gurus.weight.features.goal.components.GoalMilestoneDisplay
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Goal progress milestone card that shows weight progress with badge support.
 *
 * @param progress Progress data containing weight information
 * @param inEditMode Whether the dashboard is in edit mode
 * @param isDragging Whether the card is being dragged
 * @param isVisible Whether the milestone is currently visible
 * @param latestWeight Latest weight value for display
 * @param modifier Modifier for the composable
 * @param onBadgeClick Callback when the badge is clicked
 * @param onNavigateToGoal Callback when navigating to goal screen
 */
@Composable
fun GoalProgressMilestoneCard(
  progress: WeightProgress,
  inEditMode: Boolean,
  isDragging: Boolean = false,
  isVisible: Boolean = true,
  latestWeight: Double? = null,
  modifier: Modifier = Modifier,
  onBadgeClick: () -> Unit = {},
  onNavigateToGoal: () -> Unit = {},
  onLongClick: (WeightProgress) -> Unit = {}
) {
  // Fine‑tunable badge offsets to match other cards' top‑right alignment
  val badgeOffsetX = (-5).dp
  val badgeOffsetY = 5.dp
  // Defensive: computations aren't used for UI anymore; GoalMilestoneDisplay owns logic.
  // Keep locals only if needed for future UI extensions.

  // Wiggle animation — rests at 0° (no wiggle) under Power Saving Mode (MOB-226).
  val wiggleAngle = powerSaveAwareInfiniteFloat(
    initialValue = -1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 100, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    restingValue = 0f,
  )
  val metricBadgeIcon = if (isVisible) AppIcons.Filled.MinusCircle else AppIcons.Filled.AddCircle
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
      if (inEditMode) {
        // Nudge the badge inwards so it visually centers on top-right like other cards
        Badge(
          containerColor = Color.Transparent,
          contentColor = Color.Transparent,
          modifier = Modifier
            .padding(0.dp)
            .offset(x = badgeOffsetX, y = badgeOffsetY)
            .size(32.dp)
            .clickable { onBadgeClick() },
        ) {
          Icon(
            painter = painterResource(id = metricBadgeIcon),
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
    Card(
      modifier = Modifier
        .wrapContentSize()
        .combinedClickable(
          enabled = true,
          onClick = {

          },
          onLongClick = {
            onLongClick(progress)
          },
        )
        .alpha(if (isVisible) 1f else 0.5f),
      colors = CardDefaults.cardColors(containerColor = MeTheme.colorScheme.primaryBackground),
    ) {
      // Reuse the shared GoalMilestoneDisplay to ensure consistent look and logic.
      // If we have the required account/goal context, render; otherwise, no-op.
      val account = progress.goal?.account
      if (account != null && account.goalType != null) {
        GoalMilestoneDisplay(
          account = account,
          latestWeight = latestWeight,
          modifier = modifier
            .fillMaxWidth(),
        )
      } else {
        EmptyGoal(
          modifier = modifier,
          onSetGoalClick = onNavigateToGoal,
          inEditMode = inEditMode,
        )
      }
    }
  }
}

