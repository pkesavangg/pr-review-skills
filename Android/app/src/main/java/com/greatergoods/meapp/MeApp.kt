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
import com.greatergoods.meapp.features.common.viewmodel.AppViewModel
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun MeApp() {
    val themeViewModel: AppViewModel = hiltViewModel()
    val uiState by themeViewModel.uiState.collectAsState()
    val topLevelBackStack = rememberTopLevelBackStack(AppRoute.Init.SampleScreen)

    MeAppTheme(themeMode = uiState.themeMode) {
        CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
            NavHost(topLevelBackStack, themeViewModel)
        }
    }
}
