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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.StatHelper
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.features.dashboard.string.DashboardString
import com.greatergoods.meapp.proto.MilestoneKey
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * Composable for the dashboard milestone section that displays weight progress and milestone stats.
 *
 * @param startWeight Starting weight as string
 * @param goalWeight Goal weight as string
 * @param currentWeight Current weight as string
 * @param inEditMode Whether the dashboard is in edit mode
 * @param visibleKeys List of currently visible dashboard keys
 * @param onMilestonesChanged Callback when visible milestones are changed (for save functionality)
 * @param modifier Modifier for the composable
 */
@Composable
fun DashboardMilestone(
    startWeight: String,
    goalWeight: String,
    currentWeight: String,
    inEditMode: Boolean = false,
    visibleKeys: List<DashboardKey> = listOf(),
    onMilestonesChanged: (List<DashboardKey>) -> Unit = { },
    modifier: Modifier = Modifier
) {
    var localVisibleKeys by remember(visibleKeys) { mutableStateOf(visibleKeys) }

    val milestoneKeys = localVisibleKeys.mapNotNull { key ->
        when (key) {
            is DashboardKey.Milestone -> key.key
            is DashboardKey.Metric -> null
        }
    }
    val visibleMilestones = StatHelper.getMilestone(
        visibleKeys = milestoneKeys,
        filterNulls = false,
    )
    val allMilestones = StatHelper.getMilestone(visibleKeys = null, filterNulls = false)
    val hiddenMilestones = allMilestones.filter { it !in visibleMilestones }

    val onMilestoneMoved = { fromVisible: Boolean, toVisible: Boolean, milestone: Stat ->
        val milestoneKey = milestone.key
        if (fromVisible && !toVisible) {
            val newKeys = localVisibleKeys.filterNot { it == milestoneKey }
            localVisibleKeys = newKeys
            onMilestonesChanged(newKeys)
        } else if (!fromVisible && toVisible) {
            val newKeys = localVisibleKeys + milestoneKey
            localVisibleKeys = newKeys
            onMilestonesChanged(newKeys)
        }
    }

    Column(modifier = modifier) {
        DashboardMilestoneGrid(
            visibleMilestones = visibleMilestones,
            hiddenMilestones = hiddenMilestones,
            inEditMode = inEditMode,
            onMilestoneMoved = onMilestoneMoved,
            startWeight = startWeight,
            goalWeight = goalWeight,
            currentWeight = currentWeight,
        )
    }

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
}

/**
 * Grid layout for displaying dashboard milestones.
 */
@Composable
private fun DashboardMilestoneGrid(
    visibleMilestones: List<Stat>,
    hiddenMilestones: List<Stat>,
    inEditMode: Boolean,
    onMilestoneMoved: (fromVisible: Boolean, toVisible: Boolean, milestone: Stat) -> Unit,
    startWeight: String = "",
    goalWeight: String = "",
    currentWeight: String = ""
) {
    var localVisibleMilestones by remember(visibleMilestones) { mutableStateOf(visibleMilestones) }
    val hapticFeedback = LocalHapticFeedback.current
    val lazyGridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(
        lazyGridState = lazyGridState,
        onMove = { from, to ->
            localVisibleMilestones = localVisibleMilestones.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            // Call onMilestonesChanged with the new order
            onMilestoneMoved(true, true, localVisibleMilestones[to.index])
        },
    )
    val minCellSize = 160.dp // Adjust as needed for design
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        state = lazyGridState,
        contentPadding = PaddingValues(MeTheme.spacing.sm),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp),
        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    ) {
        // Visible milestones (reorderable)
        items(
            items = localVisibleMilestones,
            key = { getMilestoneKey(it, isVisible = true) },
            span = { milestone ->
                if (isGoalProgressMilestone(milestone)) {
                    GridItemSpan(2)
                } else {
                    GridItemSpan(1)
                }
            },
        ) { milestone ->
            ReorderableItem(
                state = reorderableState,
                key = getMilestoneKey(milestone, isVisible = true),
                enabled = inEditMode,
            ) { isDragging ->
                if (isGoalProgressMilestone(milestone)) {
                    GoalProgressMilestoneCard(
                        startWeight = startWeight,
                        goalWeight = goalWeight,
                        currentWeight = currentWeight,
                        inEditMode = inEditMode,
                        modifier = Modifier.draggableHandle(
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            },
                        ),
                        onBadgeClick = {
                            onMilestoneMoved(true, false, milestone)
                        },
                    )
                } else {
                    AnimatedStatCard(
                        stat = milestone,
                        inEditMode = inEditMode,
                        isDragging = isDragging,
                        isSelected = false,
                        modifier = Modifier.draggableHandle(
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            },
                        ),
                        onBadgeClick = {
                            onMilestoneMoved(true, false, milestone)
                        },
                    )
                }
            }
        }
        // Hidden milestones (not reorderable)
        if (inEditMode) {
            items(
                items = hiddenMilestones,
                key = { stat -> getMilestoneKey(stat, isVisible = false) },
                span = { milestone ->
                    if (isGoalProgressMilestone(milestone)) {
                        GridItemSpan(2)
                    } else {
                        GridItemSpan(1)
                    }
                },
            ) { milestone ->
                if (isGoalProgressMilestone(milestone)) {
                    GoalProgressMilestoneCard(
                        startWeight = startWeight,
                        goalWeight = goalWeight,
                        currentWeight = currentWeight,
                        inEditMode = true,
                        isVisible = false,
                        onBadgeClick = {
                            onMilestoneMoved(false, true, milestone)
                        },
                    )
                } else {
                    AnimatedStatCard(
                        stat = milestone,
                        isVisible = false,
                        inEditMode = true,
                        isSelected = null,
                        onBadgeClick = {
                            onMilestoneMoved(false, true, milestone)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Checks if a milestone is the goal progress milestone (TOTAL_CHANGE).
 */
private fun isGoalProgressMilestone(milestone: Stat): Boolean {
    return when (milestone.key) {
        is DashboardKey.Milestone -> milestone.key.key == MilestoneKey.TO_GOAL
        is DashboardKey.Metric -> false
    }
}

/**
 * Generates a unique key for milestone items in the grid.
 */
private fun getMilestoneKey(stat: Stat, isVisible: Boolean): String {
    val prefix = if (isVisible) "visible" else "hidden"
    return when (stat.key) {
        is DashboardKey.Metric -> "$prefix-${stat.key.key.name}"
        is DashboardKey.Milestone -> "$prefix-${stat.key.key.name}"
    }
}

/**
 * Goal progress milestone card that shows weight progress with badge support.
 */
@Composable
private fun GoalProgressMilestoneCard(
    startWeight: String,
    goalWeight: String,
    currentWeight: String,
    inEditMode: Boolean,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier,
    onBadgeClick: () -> Unit = {}
) {
    val start = startWeight.toFloatOrNull() ?: 0f
    val goal = goalWeight.toFloatOrNull() ?: 0f
    val current = currentWeight.toFloatOrNull() ?: 0f

    // Calculate lbsToGoal (difference between current and goal)
    val lbsToGoal = (goal - current).let { if (it > 0) it else 0f }
    val lbsToGoalText = String.format("%.1f", lbsToGoal)

    // Calculate progress: (start - current) / (start - goal)
    val totalToLose = (start - goal).let { if (it != 0f) it else 1f } // avoid division by zero
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
                        text = startWeight,
                        style = MeTheme.typography.body2,
                        color = MeTheme.colorScheme.textSubheading,
                    )
                    Text(
                        text = goalWeight,
                        style = MeTheme.typography.body2,
                        color = MeTheme.colorScheme.textSubheading,
                    )
                }
            }
        }
    }
}

@PreviewTheme
@Composable
private fun DashboardMilestonePreview() {
    MeAppTheme {
        DashboardMilestone(
            startWeight = "154.3",
            goalWeight = "132.3",
            currentWeight = "145.1",
        )
    }
}
