package com.greatergoods.meapp.domain.model.common

import com.greatergoods.meapp.core.navigation.AppRoute

data class BottomNavItem(
    val route: AppRoute,
    val icon: Int,
    val selectedIcon: Int? = null,
    val isBadgeVisible: Boolean = false,
    val label: String
)
