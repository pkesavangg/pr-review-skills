package com.dmdbrands.gurus.weight.features.dashboard.components

import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.proto.MilestoneKey

/**
 * Checks if a milestone is the goal progress milestone (TO_GOAL).
 *
 * @param milestone The milestone stat to check
 * @return True if the milestone is the goal progress milestone, false otherwise
 */
fun isGoalProgressMilestone(milestone: Stat): Boolean {
    return when (milestone.key) {
        is DashboardKey.Milestone -> milestone.key.key == MilestoneKey.TO_GOAL
        is DashboardKey.Metric -> false
    }
}

/**
 * Generates a unique key for milestone items in the grid.
 *
 * @param stat The stat to generate a key for
 * @param isVisible Whether the stat is currently visible
 * @return A unique string key for the stat
 */
fun getMilestoneKey(stat: Stat, isVisible: Boolean): String {
    val prefix = if (isVisible) "visible" else "hidden"
    return when (stat.key) {
        is DashboardKey.Metric -> "$prefix-${stat.key.key.name}"
        is DashboardKey.Milestone -> "$prefix-${stat.key.key.name}"
    }
}
