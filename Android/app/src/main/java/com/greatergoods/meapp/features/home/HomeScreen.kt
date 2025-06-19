package com.greatergoods.meapp.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import com.greatergoods.meapp.app.components.HomeNavHost
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.MainBottomNav

/**
 * Home screen displaying current user data, logout option, and switch account section.
 */
@Composable
fun HomeScreen(
) {
    val topLevelBackStack = LocalNavBackStack.current
    Scaffold(
        bottomBar = {
            MainBottomNav()
        },
    ) {
        Surface(
            modifier = Modifier
                .padding(bottom = it.calculateBottomPadding())
                .background(Red),
        ) {
            HomeNavHost(
                topLevelBackStack = topLevelBackStack,
            )
        }
    }
}
