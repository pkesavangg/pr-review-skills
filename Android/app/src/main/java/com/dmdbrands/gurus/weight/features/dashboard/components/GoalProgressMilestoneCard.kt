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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
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
  val start = progress.initWt.toFloat()
  val goalWeight = progress.goal?.goalWeight?.toFloat() ?: 0f
  val current =
    if (progress.latest != null && progress.latest is ScaleEntry) progress.latest.scale.scaleEntry.weight.toFloat() else 0f

  // Calculate lbsToGoal (difference between current and goal)
  val lbsToGoal = (goalWeight - current).let { if (it > 0) it else 0f }
  val lbsToGoalText = String.format("%.1f", lbsToGoal)

  // Calculate progress: (start - current) / (start - goal)
  val totalToLose = (start - goalWeight).let { if (it != 0f) it else 1f } // avoid division by zero
  val lost = (start - current).let { if (it > 0) it else 0f }
  val goalProgress = (lost / totalToLose).coerceIn(0f, 1f)

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
        Badge(
          containerColor = MeTheme.colorScheme.inverseAction,
          contentColor = Color.Transparent,
          modifier = Modifier
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
        .fillMaxSize()
        .alpha(if (isVisible) 1f else 0.5f),
      shape = RoundedCornerShape(MeTheme.borderRadius.sm),
      colors = CardDefaults.cardColors(containerColor = MeTheme.colorScheme.primaryBackground),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(MeTheme.spacing.md),
        horizontalAlignment = Alignment.Start,
      ) {
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = lbsToGoalText,
            color = MeTheme.colorScheme.textHeading,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.size(MeTheme.spacing.xs))
          Text(
            text = DashboardString.MileStone.LbsToGoal,
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.padding(bottom = 4.dp),
          )
        }
        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
        LinearProgressIndicator(
          progress = { goalProgress },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
          color = MeTheme.colorScheme.success,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = start.toString(),
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textSubheading,
          )
          Text(
            text = goalWeight.toString(),
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textSubheading,
          )
        }
      }
    }
  }
}
