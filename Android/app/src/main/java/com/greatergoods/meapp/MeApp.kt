package com.greatergoods.meapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.core.navigation.rememberTopLevelBackStack
import com.greatergoods.meapp.features.auth.AppViewModel
import com.greatergoods.meapp.features.common.sample.form.SampleFormScreen
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Main app composable. Sets up theme, navigation, and global dialog queue host.
 */
@Composable
fun MeApp() {
    val appViewModel: AppViewModel = hiltViewModel()
    val uiState by appViewModel.uiState.collectAsState()
    val topLevelBackStack = rememberTopLevelBackStack(AppRoute.Init.Loading)

    MeAppTheme(themeMode = uiState.themeMode) {
        CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
            DialogHost()
            NavHost(topLevelBackStack, appViewModel)
        }
    }
}
