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
import com.greatergoods.meapp.features.theme.ThemeViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import com.greatergoods.meapp.core.di.LogManagerEntryPoint

/**
 * Main app composable. Sets up theme, navigation, and global dialog queue host.
 */
@Composable
fun MeApp() {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
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
            com.greatergoods.meapp.features.common.components.NavHost(topLevelBackStack, themeViewModel, logManager)
        }
    }
}
