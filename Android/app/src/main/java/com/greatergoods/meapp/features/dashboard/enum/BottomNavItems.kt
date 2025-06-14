package com.greatergoods.meapp.features.dashboard.enum

import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.model.common.BottomNavItem
import com.greatergoods.meapp.features.dashboard.string.DashboardString
import com.greatergoods.meapp.resources.AppIcons

val BOTTOM_NAV_ITEMS =
    listOf(
        BottomNavItem(
            route = AppRoute.Main.Dashboard,
            icon = AppIcons.Default.Graph,
            selectedIcon = AppIcons.Filled.Graph,
            label = DashboardString.BottomNav.dash,
        ),
        BottomNavItem(
            route = AppRoute.Main.Entry,
            icon = AppIcons.Default.Plus,
            selectedIcon = AppIcons.Filled.Plus,
            label = DashboardString.BottomNav.entry,
        ),
        BottomNavItem(
            route = AppRoute.Main.History,
            icon = AppIcons.Default.History,
            selectedIcon = AppIcons.Filled.History,
            label = DashboardString.BottomNav.history,
        ),
        BottomNavItem(
            route = AppRoute.Main.Settings,
            icon = AppIcons.Default.Settings,
            selectedIcon = AppIcons.Filled.Settings,
            label = DashboardString.BottomNav.settings,
        ),
        BottomNavItem(
            route = AppRoute.Main.AppSync,
            icon = AppIcons.Default.Appsync,
            label = DashboardString.BottomNav.appsync,
        ),
    )
