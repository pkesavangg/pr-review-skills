package com.dmdbrands.gurus.weight.app

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
import com.dmdbrands.gurus.weight.app.components.NavHost
import com.dmdbrands.gurus.weight.app.viewmodel.AppIntent
import com.dmdbrands.gurus.weight.app.viewmodel.AppViewModel
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.DialogHost
import com.dmdbrands.gurus.weight.features.common.components.ScaleDiscoveredModal
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.example.nav3integration.rememberTopLevelBackStack
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
    if (uiState.isScaleDiscovered && uiState.hasScanStarted) {
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
