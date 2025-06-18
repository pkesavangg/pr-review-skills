package com.greatergoods.meapp.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nav3integration.rememberTopLevelBackStack
import com.greatergoods.meapp.app.components.NavHost
import com.greatergoods.meapp.app.viewmodel.AppViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.DialogHost
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Main app composable. Sets up theme, navigation, and global dialog queue host.
 * Handles window insets properly for the entire app.
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
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
            color = MeTheme.colorScheme.primaryBackground,
        ) {
            CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
                DialogHost()
                NavHost(topLevelBackStack, appViewModel)
            }
        }
    }
}
