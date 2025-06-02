package com.greatergoods.meapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.core.navigation.rememberTopLevelBackStack
import com.greatergoods.meapp.features.common.components.NavHost
import com.greatergoods.meapp.features.theme.ThemeViewModel
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun MeApp() {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val topLevelBackStack = rememberTopLevelBackStack(AppRoute.Init.SampleScreen)

    MeAppTheme(themeMode = themeMode) {
        CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
            NavHost(topLevelBackStack, themeViewModel)
        }
    }
}
