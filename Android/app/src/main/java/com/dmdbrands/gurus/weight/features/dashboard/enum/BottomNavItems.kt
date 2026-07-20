package com.dmdbrands.gurus.weight.features.dashboard.enum

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.domain.model.common.BottomNavItem
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
import com.dmdbrands.gurus.weight.resources.AppIcons

val BOTTOM_NAV_ITEMS =
    listOf(
        BottomNavItem(
            route = AppRoute.Main.Dashboard,
            tabId = "dashboard",
            icon = AppIcons.Default.Graph,
            selectedIcon = AppIcons.Filled.Graph,
            label = DashboardString.BottomNav.dash,
        ),
        BottomNavItem(
          route = AppRoute.Main.Entry,
          tabId = "entry",
          icon = AppIcons.Outlined.PlusCircle,
          selectedIcon = AppIcons.Filled.Plus,
          label = DashboardString.BottomNav.entry,
        ),
        BottomNavItem(
            route = AppRoute.Main.History,
            tabId = "history",
            icon = AppIcons.Default.History,
            selectedIcon = AppIcons.Filled.History,
            label = DashboardString.BottomNav.history,
        ),
        BottomNavItem(
            route = AppRoute.Main.Settings,
            tabId = "settings",
            icon = AppIcons.Default.Settings,
            selectedIcon = AppIcons.Filled.Settings,
            label = DashboardString.BottomNav.settings,
        ),
        BottomNavItem(
            route = AppRoute.Main.AppSync,
            tabId = "appsync",
            icon = AppIcons.Default.Appsync,
            label = DashboardString.BottomNav.appsync,
        ),
    )
