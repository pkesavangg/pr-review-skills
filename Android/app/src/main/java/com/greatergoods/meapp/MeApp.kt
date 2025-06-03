package com.greatergoods.meapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.core.navigation.rememberTopLevelBackStack
import com.greatergoods.meapp.features.common.components.DialogQueueHost
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel
import com.greatergoods.meapp.features.common.viewmodel.AppViewModel
import com.greatergoods.meapp.features.common.viewmodel.NavigationViewmodel
import com.greatergoods.meapp.theme.MeAppTheme
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import com.greatergoods.meapp.core.di.LogManagerEntryPoint
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect

/**
 * Main app composable. Sets up theme, navigation, and global dialog queue host.
 */
@Composable
fun MeApp() {
    val appViewModel: AppViewModel = hiltViewModel()
    val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
    val uiState by appViewModel.uiState.collectAsState()
    val themeMode = uiState.themeMode
    val topLevelBackStack = rememberTopLevelBackStack(AppRoute.Init.SampleScreen)
    val context = LocalContext.current
    val logManager = EntryPointAccessors.fromApplication(
        context.applicationContext,
        LogManagerEntryPoint::class.java
    ).logManager()

    MeAppTheme(themeMode = themeMode) {
        // Global dialog host
        DialogQueueHost(dialogQueueViewModel)
        CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
            NavHost(topLevelBackStack, appViewModel, logManager)
        }
    }
}
