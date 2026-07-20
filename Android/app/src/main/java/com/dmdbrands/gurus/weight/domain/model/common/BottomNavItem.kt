package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.core.navigation.AppRoute

/**
 * Model for a bottom navigation item.
 *
 * @property route The navigation route for this item.
 * @property tabId Stable, mode-independent key for this tab, used to build its automation
 * testTag. Unlike [route] (which the dashboard entry swaps between `Dashboard` and
 * `DashboardSnapshot` by mode), this never changes, so QA can target a tab with one id.
 * @property icon The icon resource ID for the item.
 * @property selectedIcon The icon resource ID when selected (optional).
 * @property label The label for the item.
 * @property isBadgeVisible Whether a badge/dot should be shown.
 * @property badgeCount Optional badge count (if >0, show count; if ==0 and isBadgeVisible, show dot).
 */
data class BottomNavItem(
    val route: AppRoute,
    val tabId: String,
    val icon: Int,
    val selectedIcon: Int? = null,
    val label: String,
)
