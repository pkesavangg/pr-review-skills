package com.greatergoods.meapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nav3integration.rememberTopLevelBackStack
import com.greatergoods.meapp.app.components.NavHost
import com.greatergoods.meapp.app.viewmodel.AppViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.DialogHost
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Main app composable. Sets up theme, navigation, and global dialog queue host.
 */
@Composable
fun MeApp() {
    val appViewModel: AppViewModel = hiltViewModel()
    val uiState by appViewModel.state.collectAsState()
    val topLevelBackStack =
        rememberTopLevelBackStack(
            Pair(AppRoute.App, AppRoute.Init.Loading),
            AppRoute.Auth.Login,
            Pair(AppRoute.Home, AppRoute.Main.Dashboard),
        )
    MeAppTheme(themeMode = uiState.themeMode) {
        CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
            DialogHost()
            NavHost(topLevelBackStack, appViewModel)
        }
    }
}
