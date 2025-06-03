package com.greatergoods.meapp

import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.core.navigation.rememberTopLevelBackStack
import com.greatergoods.meapp.features.common.components.DialogQueueHost
import com.greatergoods.meapp.features.common.viewmodel.AppViewModel
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Main app composable. Sets up theme, navigation, and global dialog queue host.
 */
@Composable
fun MeApp() {
    val appViewModel: AppViewModel = hiltViewModel()
    val uiState by appViewModel.uiState.collectAsState()
    val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
    val themeMode = uiState.themeMode
    val topLevelBackStack = rememberTopLevelBackStack(AppRoute.Init.SampleScreen)

    MeAppTheme(themeMode = themeMode) {
        // Global dialog host
        DialogQueueHost(dialogQueueViewModel) { dialog ->
            // Custom dialog content can be provided here if needed
            when (dialog.contentKey) {
                "custom_dialog" -> {
                    Text("Custom dialog: ${dialog.params}", modifier = Modifier.background(Red))
                }

                else -> {
                    // Default dialog handling
                    // This can be a placeholder or a default dialog implementation
                }
            }

        }
        CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
            com.greatergoods.meapp.features.common.components.NavHost(topLevelBackStack, appViewModel)
        }
    }
}
