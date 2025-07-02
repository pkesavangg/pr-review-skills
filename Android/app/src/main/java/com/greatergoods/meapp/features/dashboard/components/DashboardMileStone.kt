package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.StatHelper
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.features.dashboard.string.DashboardString
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

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
    onMilestonesChanged: (List<Stat>) -> Unit = { },
    modifier: Modifier = Modifier
) {
    val milestoneState = rememberMilestoneState(
        visibleKeys = visibleKeys,
        inEditMode = inEditMode,
    )

    // Notify parent when visible milestones change
    LaunchedEffect(milestoneState.visibleMilestones) {
        onMilestonesChanged(milestoneState.visibleMilestones)
    }

    Column(modifier = modifier) {
        // Weight progress card
        WeightProgressCard(
            startWeight = startWeight,
            goalWeight = goalWeight,
            currentWeight = currentWeight,
        )

        // Milestones grid
        DashboardMilestoneGrid(
            visibleMilestones = milestoneState.visibleMilestones,
            hiddenMilestones = milestoneState.hiddenMilestones,
            inEditMode = inEditMode,
            onMilestoneMoved = { fromVisible, toVisible, milestone ->
                if (fromVisible && !toVisible) {
                    milestoneState.moveToHidden(milestone)
                } else if (!fromVisible && toVisible) {
                    milestoneState.moveToVisible(milestone)
                }
            },
        )
    }

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
}

/**
 * Internal state management for dashboard milestones.
 */
@Composable
private fun rememberMilestoneState(
    visibleKeys: List<DashboardKey>,
    inEditMode: Boolean
): MilestoneState {
    val milestoneKeys = visibleKeys.mapNotNull { key ->
        when (key) {
            is DashboardKey.Milestone -> key.key
            is DashboardKey.Metric -> null
        }
    }

    val allMilestones = StatHelper.getMilestone(useShort = true, filterNulls = false)

    val initialVisibleMilestones = if (milestoneKeys.isNotEmpty()) {
        allMilestones.filter { stat ->
            when (stat.key) {
                is DashboardKey.Milestone -> stat.key.key in milestoneKeys
                is DashboardKey.Metric -> false
            }
        }
    } else {
        allMilestones
    }

    val initialHiddenMilestones = allMilestones.filter { it !in initialVisibleMilestones }

    var visibleMilestones by remember(initialVisibleMilestones) { mutableStateOf(initialVisibleMilestones) }
    var hiddenMilestones by remember(initialHiddenMilestones) { mutableStateOf(initialHiddenMilestones) }

    return remember(visibleMilestones, hiddenMilestones) {
        MilestoneState(
            visibleMilestones = visibleMilestones,
            hiddenMilestones = hiddenMilestones,
            moveToHidden = { milestone ->
                visibleMilestones = visibleMilestones.toMutableList().apply { remove(milestone) }
                hiddenMilestones = hiddenMilestones.toMutableList().apply { add(milestone) }
            },
            moveToVisible = { milestone ->
                hiddenMilestones = hiddenMilestones.toMutableList().apply { remove(milestone) }
                visibleMilestones = visibleMilestones.toMutableList().apply { add(milestone) }
            },
        )
    }
}

/**
 * Data class to hold milestone state and operations.
 */
private data class MilestoneState(
    val visibleMilestones: List<Stat>,
    val hiddenMilestones: List<Stat>,
    val moveToHidden: (Stat) -> Unit,
    val moveToVisible: (Stat) -> Unit
)

/**
 * Weight progress card showing lbs to goal and progress bar.
 */
@Composable
private fun WeightProgressCard(
    startWeight: String,
    goalWeight: String,
    currentWeight: String,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MeTheme.spacing.sm),
        colors = CardDefaults.cardColors(containerColor = MeTheme.colorScheme.primaryBackground),
    ) {
        Column(
            modifier = Modifier.padding(MeTheme.spacing.md),
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

/**
 * Grid layout for displaying dashboard milestones.
 */
@Composable
private fun DashboardMilestoneGrid(
    visibleMilestones: List<Stat>,
    hiddenMilestones: List<Stat>,
    inEditMode: Boolean,
    onMilestoneMoved: (fromVisible: Boolean, toVisible: Boolean, milestone: Stat) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(MeTheme.spacing.sm),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    ) {
        // Visible milestones
        items(
            items = visibleMilestones,
            key = { stat -> getMilestoneKey(stat, isVisible = true) },
        ) { milestone ->
            AnimatedStatCard(
                stat = milestone,
                inEditMode = inEditMode,
                isSelected = false,
                onBadgeClick = {
                    onMilestoneMoved(true, false, milestone)
                },
            )
        }

        // Hidden milestones (only when in edit mode)
        if (inEditMode) {
            items(
                items = hiddenMilestones,
                key = { stat -> getMilestoneKey(stat, isVisible = false) },
            ) { milestone ->
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

        // Divider if total visible items are odd
        val totalVisibleItems = visibleMilestones.size + if (inEditMode) hiddenMilestones.size else 0
        if (totalVisibleItems % 2 != 0) {
            item {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MeTheme.colorScheme.utility,
                    modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
                )
            }
        }
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
