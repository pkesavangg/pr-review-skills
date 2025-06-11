package com.greatergoods.meapp.features.dashboard.helper

import com.greatergoods.meapp.R
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.model.common.BottomNavItem

val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(
        route = AppRoute.Main.Dashboard,
        icon = R.drawable.graph,
        selectedIcon = R.drawable.graph_selected,
        label = "dash",
    ),
    BottomNavItem(
        route = AppRoute.Main.Entry,
        icon = R.drawable.plus,
        selectedIcon = R.drawable.plus_selected,
        label = "entry",
    ),
    BottomNavItem(
        route = AppRoute.Main.History,
        icon = R.drawable.history,
        selectedIcon = R.drawable.history_selected,
        label = "history",
    ),
    BottomNavItem(
        route = AppRoute.Main.Settings,
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings_selected,
        label = "settings",
    ),
    BottomNavItem(
        route = AppRoute.Main.AppSync,
        icon = R.drawable.appsync,
        label = "appsync",
    ),
)
