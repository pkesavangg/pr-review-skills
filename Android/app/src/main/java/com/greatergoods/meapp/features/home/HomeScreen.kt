package com.greatergoods.meapp.features.home

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.app.components.HomeNavHost
import com.greatergoods.meapp.features.common.components.MainBottomNav
import android.annotation.SuppressLint

/**
 * Home screen displaying current user data, logout option, and switch account section.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreen(
) {
    val topLevelBackStack = LocalNavBackStack.current
    Scaffold(
        bottomBar = {
            MainBottomNav()
        },
    ) {
        HomeNavHost(
            topLevelBackStack = topLevelBackStack,
        )
    }
}
