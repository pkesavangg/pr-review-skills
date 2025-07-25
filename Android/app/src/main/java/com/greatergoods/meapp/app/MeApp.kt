package com.greatergoods.meapp.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nav3integration.rememberTopLevelBackStack
import com.greatergoods.meapp.app.components.NavHost
import com.greatergoods.meapp.app.viewmodel.AppIntent
import com.greatergoods.meapp.app.viewmodel.AppViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.DialogHost
import com.greatergoods.meapp.features.common.components.ScaleDiscoveredModal
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.delay

/**
 * Main app composable. Sets up theme, navigation, and global dialog queue host.
 * Handles window insets properly for the entire app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeApp() {
  val appViewModel: AppViewModel = hiltViewModel()
  val uiState by appViewModel.state.collectAsState()
  val topLevelBackStack =
    rememberTopLevelBackStack(
      Pair(AppRoute.App, AppRoute.Init.Loading),
      AppRoute.Auth.Login(),
      Pair(AppRoute.Home, AppRoute.Main.Dashboard),
    )

  MeAppTheme(themeMode = uiState.themeMode) {
    Surface(
      modifier =
        Modifier
          .fillMaxSize()
          .imePadding(),
      color = MeTheme.colorScheme.primaryBackground,
    ) {
      CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
        DialogHost()
        NavHost(topLevelBackStack, appViewModel)
      }
    }
    if (uiState.isScaleDiscovered) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      LaunchedEffect(Unit) {
        delay(15 * 1000)
        appViewModel.handleIntent(AppIntent.OnPopUpDismiss)
      }
      ModalBottomSheet(
        sheetState = sheetState,
        modifier = Modifier.navigationBarsPadding(),
        onDismissRequest = { appViewModel.handleIntent(AppIntent.OnPopUpDismiss) },
        containerColor = MeTheme.colorScheme.primaryBackground,
      ) {
        ScaleDiscoveredModal(sku = uiState.sku) {
          appViewModel.handleIntent(AppIntent.OnPopUpConnect)
        }
      }
    }
  }
}
